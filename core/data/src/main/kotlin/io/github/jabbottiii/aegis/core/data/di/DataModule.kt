package io.github.jabbottiii.aegis.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jabbottiii.aegis.core.common.coroutines.AegisDispatchers
import io.github.jabbottiii.aegis.core.common.logging.AegisLogger
import io.github.jabbottiii.aegis.core.data.settings.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** The settings [DataStore]. Features reach it through repositories in this module, never directly. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class SettingsPreferences

@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {
    @Provides
    @Singleton
    @SettingsPreferences
    fun provideSettings(
        @ApplicationContext context: Context,
        dispatchers: AegisDispatchers,
        logger: AegisLogger,
    ): DataStore<Preferences> =
        settingsDataStore(
            // filesDir/datastore/settings.preferences_pb
            file = context.preferencesDataStoreFile("settings"),
            // Lives as long as the process, like the store.
            scope = CoroutineScope(SupervisorJob() + dispatchers.io),
            logger = logger,
        )
}
