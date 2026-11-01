package io.github.jabbottiii.aegis.core.security.di

import android.content.Context
import android.content.pm.PackageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jabbottiii.aegis.core.common.coroutines.AegisDispatchers
import io.github.jabbottiii.aegis.core.common.logging.AegisLogger
import io.github.jabbottiii.aegis.core.security.keystore.KeystoreKeyWrapper
import io.github.jabbottiii.aegis.core.security.secret.SecretStore
import io.github.jabbottiii.aegis.core.security.secret.WrappedSecretStore
import io.github.jabbottiii.aegis.core.security.secret.fsyncDirectory
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object SecurityModule {
    /** Under `noBackupFilesDir`, which Auto Backup and device transfer never copy (NFR-PRIV-04). */
    private const val SECRETS_DIRECTORY = "aegis-secrets"

    /** Changing the alias orphans every stored secret. */
    private const val WRAPPING_KEY_ALIAS = "aegis_secret_wrapping_v1"

    @Provides
    @Singleton
    fun provideSecretStore(
        @ApplicationContext context: Context,
        dispatchers: AegisDispatchers,
        logger: AegisLogger,
    ): SecretStore =
        WrappedSecretStore(
            directory = File(context.noBackupFilesDir, SECRETS_DIRECTORY),
            wrapper =
                KeystoreKeyWrapper(
                    alias = WRAPPING_KEY_ALIAS,
                    preferStrongBox =
                        context.packageManager.hasSystemFeature(
                            PackageManager.FEATURE_STRONGBOX_KEYSTORE,
                        ),
                    logger = logger,
                ),
            dispatchers = dispatchers,
            logger = logger,
            syncDirectory = ::fsyncDirectory,
        )
}
