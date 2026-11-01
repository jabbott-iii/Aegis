package io.github.jabbottiii.aegis.core.data.database

import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteDriver
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jabbottiii.aegis.core.common.coroutines.AegisDispatchers
import io.github.jabbottiii.aegis.core.common.logging.AegisLogger
import io.github.jabbottiii.aegis.core.data.DataLogEvent
import io.github.jabbottiii.aegis.core.security.secret.SecretFailure
import io.github.jabbottiii.aegis.core.security.secret.SecretId
import io.github.jabbottiii.aegis.core.security.secret.SecretResult
import io.github.jabbottiii.aegis.core.security.secret.SecretStore
import kotlinx.coroutines.withContext
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.LogTarget
import net.zetetic.database.Logger
import net.zetetic.database.sqlcipher.CloseGuard
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver
import java.io.File
import java.nio.file.Files
import javax.inject.Inject
import android.database.sqlite.SQLiteException as PlatformSQLiteException

/** The result of opening an encrypted database. */
internal sealed interface DatabaseResult<out T> {
    data class Opened<T>(
        val database: T,
    ) : DatabaseResult<T>

    /** core:security couldn't supply the key. [failure] says whether a retry can help. */
    data class KeyUnavailable(
        val failure: SecretFailure,
    ) : DatabaseResult<Nothing>

    /**
     * The file exists, but this key can't open it: the key changed, the key was lost and replaced,
     * or the file is corrupt. The file is kept.
     */
    data object Unreadable : DatabaseResult<Nothing>

    /** SQLite couldn't open the file for another reason, such as a full disk. A later try may work. */
    data object OpenFailed : DatabaseResult<Nothing>
}

/**
 * Opens Room databases encrypted with SQLCipher (ARCHITECTURE §6). Each database's key is a secret
 * from core:security, handed to SQLCipher as a raw key.
 *
 * It never deletes a database. SQLCipher's default error handler deletes any file it can't open,
 * and a wrong key looks the same as corruption, so one lost key would silently wipe the user's
 * findings and trust decisions. What to do with an unreadable database is the caller's decision.
 */
internal class EncryptedDatabaseFactory
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val secrets: SecretStore,
        private val dispatchers: AegisDispatchers,
        private val logger: AegisLogger,
    ) {
        init {
            // SQLCipher doesn't load its native library itself. Repeated calls are no-ops.
            System.loadLibrary("sqlcipher")
            // SQLCipher's Java layer logs to logcat by default. Route it to debug text, which
            // release builds drop (CLAUDE.md non-negotiable #8). Its native SQLite error log
            // can't be redirected (ARCHITECTURE §6).
            Logger.setTarget(DebugOnlyLogTarget(logger))
            CloseGuard.setReporter { message, _ ->
                logger.warn(DataLogEvent.DATABASE_NOT_CLOSED)
                logger.debug { message }
            }
        }

        /**
         * Opens the database file [name] (under `getDatabasePath`) as a [type], keyed by [secretId].
         *
         * Room options come only through the parameters here, never through the builder itself.
         * Several builder options delete or empty a database: destructive migration drops every
         * table, and a prepackaged database (`createFromAsset` and the like) can delete the file
         * when a migration is due. Neither is ever set.
         */
        suspend fun <T : RoomDatabase> open(
            name: String,
            type: Class<T>,
            secretId: SecretId,
            migrations: List<Migration> = emptyList(),
        ): DatabaseResult<T> {
            val available =
                when (val result = secrets.getOrCreate(secretId)) {
                    is SecretResult.Unavailable -> return DatabaseResult.KeyUnavailable(result.failure)
                    is SecretResult.Available -> result
                }
            val key = rawKey(available)
            // The driver keeps the key for as long as the database is open, because every new
            // connection needs it. Every other exit overwrites it.
            var handedOver = false
            return try {
                withContext(dispatchers.io) {
                    val file = context.getDatabasePath(name)
                    val driver = SQLCipherDriver(key, null, KeepFileOnCorruption)
                    val failure =
                        if (available.isNew && !Files.notExists(file.toPath())) {
                            // The database outlived its key, so a new key was just made. It can't open the file.
                            unreadable(DataLogEvent.DATABASE_KEY_REPLACED, cause = null)
                        } else {
                            verify(driver, file)
                        }
                    failure ?: DatabaseResult.Opened(build(type, name, driver, migrations)).also { handedOver = true }
                }
            } finally {
                if (!handedOver) key.fill(0)
            }
        }

        private fun <T : RoomDatabase> build(
            type: Class<T>,
            name: String,
            driver: SQLiteDriver,
            migrations: List<Migration>,
        ): T {
            val builder = Room.databaseBuilder(context, type, name)
            migrations.forEach { builder.addMigrations(it) }
            return builder
                .setDriver(driver)
                .setQueryCoroutineContext(dispatchers.io)
                .build()
        }

        private fun rawKey(result: SecretResult.Available): ByteArray {
            val secret = result.secret.copyBytes()
            result.secret.wipe()
            return try {
                sqlCipherRawKey(secret)
            } finally {
                secret.fill(0)
            }
        }

        /**
         * Opens one connection and reads the schema, which only works with the right key. Room
         * would otherwise find out on its first query, too late to report a typed result.
         * Returns null when the key works. A missing file is created, empty and encrypted.
         */
        private fun verify(
            driver: SQLiteDriver,
            file: File,
        ): DatabaseResult<Nothing>? =
            try {
                file.parentFile?.mkdirs()
                driver.open(file.path).use { connection ->
                    connection.prepare("SELECT count(*) FROM sqlite_master").use { it.step() }
                }
                null
            } catch (e: SQLiteDatabaseCorruptException) {
                unreadable(DataLogEvent.DATABASE_UNREADABLE, e)
            } catch (e: SQLiteNotADatabaseException) {
                unreadable(DataLogEvent.DATABASE_UNREADABLE, e)
            } catch (e: SQLException) {
                logger.error(DataLogEvent.DATABASE_OPEN_FAILED, cause = e)
                DatabaseResult.OpenFailed
            }

        private fun unreadable(
            event: DataLogEvent,
            cause: Throwable?,
        ): DatabaseResult<Nothing> {
            logger.error(event, cause = cause)
            return DatabaseResult.Unreadable
        }
    }

/** Leaves the file alone. The open that hit the problem fails, and the factory reports it. */
private object KeepFileOnCorruption : DatabaseErrorHandler {
    override fun onCorruption(
        dbObj: SQLiteDatabase,
        exception: PlatformSQLiteException,
    ) {
        // Deliberately empty: SQLCipher's default handler would delete the database here.
    }
}

/** SQLCipher's messages can include paths and SQL, so they only reach debug builds. */
private class DebugOnlyLogTarget(
    private val logger: AegisLogger,
) : LogTarget {
    override fun isLoggable(
        tag: String,
        priority: Int,
    ): Boolean = priority >= Logger.WARN

    override fun log(
        priority: Int,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        logger.debug { "$tag: $message" }
    }
}
