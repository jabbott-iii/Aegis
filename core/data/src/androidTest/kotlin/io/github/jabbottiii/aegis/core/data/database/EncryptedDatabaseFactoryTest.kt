package io.github.jabbottiii.aegis.core.data.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.jabbottiii.aegis.core.common.logging.LogPolicy
import io.github.jabbottiii.aegis.core.common.logging.PolicyLogger
import io.github.jabbottiii.aegis.core.security.secret.Secret
import io.github.jabbottiii.aegis.core.security.secret.SecretFailure
import io.github.jabbottiii.aegis.core.security.secret.SecretId
import io.github.jabbottiii.aegis.core.security.secret.SecretResult
import io.github.jabbottiii.aegis.core.security.secret.SecretStore
import io.github.jabbottiii.aegis.testing.common.RecordingLogSink
import io.github.jabbottiii.aegis.testing.common.TestAegisDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import android.database.sqlite.SQLiteDatabase as PlatformSQLiteDatabase

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseFactoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sink = RecordingLogSink()
    private val logger = PolicyLogger(sink, LogPolicy.DEBUG)
    private val file: File get() = context.getDatabasePath(NAME)

    private val keyA = ByteArray(32) { 0x11 }
    private val keyB = ByteArray(32) { 0x22 }

    private fun TestScope.factory(result: () -> SecretResult) =
        EncryptedDatabaseFactory(context, FakeSecretStore(result), TestAegisDispatchers(this), logger)

    private fun TestScope.factory(key: ByteArray) =
        factory { SecretResult.Available(Secret(key.copyOf()), isNew = false) }

    private suspend fun EncryptedDatabaseFactory.openProbe() =
        open(NAME, ProbeDatabase::class.java, SecretId.APP_DATABASE)

    private suspend fun EncryptedDatabaseFactory.openedProbe(): ProbeDatabase =
        assertIs<DatabaseResult.Opened<ProbeDatabase>>(openProbe()).database

    private fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(NAME)
    }

    @Test
    fun opensAnEncryptedDatabaseThatRoomCanUse() =
        runTest {
            val database = factory(keyA).openedProbe()

            database.probeDao().insert(ProbeRow(id = 1, label = "first"))

            assertEquals("first", database.probeDao().find(1)?.label)
            database.close()
            val header = file.readBytes().copyOfRange(0, 16)
            assertFalse(header.contentEquals(PLAINTEXT_HEADER), "The file starts with SQLite's plaintext header")
        }

    @Test
    fun rowTextNeverReachesTheFileInPlaintext() =
        runTest {
            val database = factory(keyA).openedProbe()
            database.probeDao().insert(ProbeRow(id = 1, label = MARKER))
            database.close()

            val files = file.parentFile?.listFiles { candidate -> candidate.name.startsWith(NAME) }.orEmpty()

            assertTrue(files.isNotEmpty())
            files.forEach { written ->
                val text = written.readBytes().toString(Charsets.ISO_8859_1)
                assertFalse(text.contains(MARKER), "${written.name} holds row text in plaintext")
            }
        }

    @Test
    fun reopensWithTheSameKey() =
        runTest {
            factory(keyA).openedProbe().apply {
                probeDao().insert(ProbeRow(id = 7, label = "kept"))
                close()
            }

            val reopened = factory(keyA).openedProbe()

            assertEquals("kept", reopened.probeDao().find(7)?.label)
            reopened.close()
        }

    @Test
    fun aDifferentKeyIsUnreadableAndTheFileIsKept() =
        runTest {
            factory(keyA).openedProbe().apply {
                probeDao().insert(ProbeRow(id = 1, label = "kept"))
                close()
            }
            val before = sha256(file)

            val result = factory(keyB).openProbe()

            assertEquals(DatabaseResult.Unreadable, result)
            assertContentEquals(before, sha256(file))
            assertEquals("DATA.DATABASE_UNREADABLE", sink.records.last().event)
            val reopened = factory(keyA).openedProbe()
            assertEquals("kept", reopened.probeDao().find(1)?.label)
            reopened.close()
        }

    @Test
    fun aNewKeyForAnExistingDatabaseIsUnreadableWithoutTouchingTheFile() =
        runTest {
            factory(keyA).openedProbe().close()
            val before = sha256(file)

            val result = factory { SecretResult.Available(Secret(keyB.copyOf()), isNew = true) }.openProbe()

            assertEquals(DatabaseResult.Unreadable, result)
            assertEquals("DATA.DATABASE_KEY_REPLACED", sink.records.last().event)
            assertContentEquals(before, sha256(file))
        }

    @Test
    fun aPlaintextDatabaseIsUnreadableAndKept() =
        runTest {
            file.parentFile?.mkdirs()
            PlatformSQLiteDatabase.openOrCreateDatabase(file, null).use { it.execSQL("CREATE TABLE t (x INTEGER)") }
            val before = sha256(file)

            val result = factory(keyA).openProbe()

            assertEquals(DatabaseResult.Unreadable, result)
            assertContentEquals(before, sha256(file))
        }

    @Test
    fun anUnavailableKeyOpensNothing() =
        runTest {
            val result = factory { SecretResult.Unavailable(SecretFailure.KEY_LOST) }.openProbe()

            assertEquals(DatabaseResult.KeyUnavailable(SecretFailure.KEY_LOST), result)
            assertFalse(file.exists())
        }

    @Test
    fun theFactoryWipesTheSecretItWasGiven() =
        runTest {
            val secret = Secret(keyA.copyOf())

            val database =
                assertIs<DatabaseResult.Opened<ProbeDatabase>>(
                    factory { SecretResult.Available(secret, isNew = true) }.openProbe(),
                ).database

            assertTrue(secret.copyBytes().all { it == 0.toByte() })
            database.close()
        }

    private class FakeSecretStore(
        private val result: () -> SecretResult,
    ) : SecretStore {
        override suspend fun getOrCreate(id: SecretId): SecretResult = result()
    }

    private companion object {
        const val NAME = "encrypted-factory-test.db"
        const val MARKER = "aegis-probe-plaintext-marker"
        val PLAINTEXT_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
