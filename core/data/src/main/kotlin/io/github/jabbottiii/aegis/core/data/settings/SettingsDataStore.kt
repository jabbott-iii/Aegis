package io.github.jabbottiii.aegis.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import io.github.jabbottiii.aegis.core.common.logging.AegisLogger
import io.github.jabbottiii.aegis.core.data.DataLogEvent
import kotlinx.coroutines.CoroutineScope
import java.io.File

/** Present and true after a corrupt settings file was replaced with defaults. */
internal val SETTINGS_RESET_MARKER = booleanPreferencesKey("aegis_settings_reset_after_corruption")

/**
 * The settings store (docs/privacy.md, "Data inventory"). It's multi-process because `:shield`
 * reads settings too, and two single-process DataStores on one file would overwrite each other's
 * changes. Each process must create exactly one, this way.
 *
 * Settings aren't encrypted, so they hold only choices: booleans, numbers, and enum names. Anything
 * that identifies the user's apps, sites, or content (package names, domains, URLs, free text) goes
 * in an encrypted database instead (docs/01-architecture.md, "Data model"). [file] must be under
 * `filesDir`, which the backup rules exclude like every other domain (NFR-PRIV-04).
 *
 * A corrupt file is replaced with the shipped defaults plus [SETTINGS_RESET_MARKER], and that's
 * logged. A reset never turns on something the user didn't choose, but it can turn off a
 * protection they did. The first feature that keeps a protection's on/off state here must check
 * the marker, tell the user, and then clear it, so a protection never stops silently. No such
 * setting exists yet.
 */
internal fun settingsDataStore(
    file: File,
    scope: CoroutineScope,
    logger: AegisLogger,
): DataStore<Preferences> =
    MultiProcessDataStoreFactory.create(
        serializer = PreferencesFileSerializer,
        corruptionHandler =
            ReplaceFileCorruptionHandler {
                logger.warn(DataLogEvent.SETTINGS_RESET_AFTER_CORRUPTION)
                preferencesOf(SETTINGS_RESET_MARKER to true)
            },
        scope = scope,
        produceFile = { file },
    )
