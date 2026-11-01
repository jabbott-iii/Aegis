package io.github.jabbottiii.aegis.core.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.jabbottiii.aegis.core.common.logging.LogLevel
import io.github.jabbottiii.aegis.core.common.logging.LogPolicy
import io.github.jabbottiii.aegis.core.common.logging.PolicyLogger
import io.github.jabbottiii.aegis.testing.common.RecordingLogSink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SettingsDataStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.filesDir, "settings-datastore-test")
    private val sink = RecordingLogSink()
    private val logger = PolicyLogger(sink, LogPolicy.DEBUG)
    private val flag = booleanPreferencesKey("test_flag")

    @Before
    @After
    fun deleteFiles() {
        directory.deleteRecursively()
    }

    @Test
    fun writesAndReadsBackASetting() =
        runTest {
            val store = settingsDataStore(File(directory, "write.preferences_pb"), backgroundScope, logger)

            store.edit { it[flag] = true }

            assertEquals(true, store.data.first()[flag])
            assertTrue(sink.records.isEmpty())
        }

    @Test
    fun aCorruptFileFallsBackToDefaultsAndIsLogged() =
        runTest {
            val file = File(directory, "corrupt.preferences_pb")
            directory.mkdirs()
            file.writeBytes(ByteArray(64) { 0xFF.toByte() })
            val store = settingsDataStore(file, backgroundScope, logger)

            val settings = store.data.first()

            assertNull(settings[flag])
            assertEquals(true, settings[SETTINGS_RESET_MARKER])
            assertEquals(listOf(LogLevel.WARN), sink.records.map { it.level })
            assertEquals("DATA.SETTINGS_RESET_AFTER_CORRUPTION", sink.records.single().event)
        }
}
