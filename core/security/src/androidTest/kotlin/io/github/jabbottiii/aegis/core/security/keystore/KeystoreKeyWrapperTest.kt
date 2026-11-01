package io.github.jabbottiii.aegis.core.security.keystore

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.crypto.tink.integration.android.AndroidKeystore
import io.github.jabbottiii.aegis.core.common.logging.LogPolicy
import io.github.jabbottiii.aegis.core.common.logging.PolicyLogger
import io.github.jabbottiii.aegis.core.security.secret.SecretFailure
import io.github.jabbottiii.aegis.core.security.secret.SecretId
import io.github.jabbottiii.aegis.core.security.secret.SecretResult
import io.github.jabbottiii.aegis.core.security.secret.WrappedSecretStore
import io.github.jabbottiii.aegis.core.security.secret.fsyncDirectory
import io.github.jabbottiii.aegis.testing.common.RecordingLogSink
import io.github.jabbottiii.aegis.testing.common.TestAegisDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.crypto.AEADBadTagException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Runs against the device's real Android Keystore, under a test-only alias. */
@RunWith(AndroidJUnit4::class)
class KeystoreKeyWrapperTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sink = RecordingLogSink()
    private val logger = PolicyLogger(sink, LogPolicy.DEBUG)
    private val directory = File(context.noBackupFilesDir, "aegis-secrets-test")
    private val associatedData = "aegis/test".toByteArray()

    private fun wrapper(preferStrongBox: Boolean = false) = KeystoreKeyWrapper(TEST_ALIAS, preferStrongBox, logger)

    private fun TestScope.store() =
        WrappedSecretStore(directory, wrapper(), TestAegisDispatchers(this), logger, ::fsyncDirectory)

    @Before
    @After
    fun removeTestKeyAndFiles() {
        if (AndroidKeystore.hasKey(TEST_ALIAS)) AndroidKeystore.deleteKey(TEST_ALIAS)
        directory.deleteRecursively()
    }

    @Test
    fun createdKeyWrapsAndUnwrapsWithAFreshIvEachTime() {
        val wrapper = wrapper()
        assertFalse(wrapper.hasKey())

        wrapper.createKey()
        val secret = ByteArray(32) { it.toByte() }
        val first = wrapper.wrap(secret, associatedData)
        val second = wrapper.wrap(secret, associatedData)

        assertTrue(wrapper.hasKey())
        assertEquals(12 + 32 + 16, first.size)
        assertFalse(first.contentEquals(second))
        assertContentEquals(secret, wrapper.unwrap(first, associatedData))
        assertContentEquals(secret, wrapper.unwrap(second, associatedData))
    }

    @Test
    fun tamperedCiphertextFailsAuthentication() {
        val wrapper = wrapper().apply { createKey() }
        val wrapped = wrapper.wrap(ByteArray(32), associatedData)
        wrapped[20] = (wrapped[20].toInt() xor 1).toByte()

        assertFailsWith<AEADBadTagException> { wrapper.unwrap(wrapped, associatedData) }
    }

    @Test
    fun differentAssociatedDataFailsAuthentication() {
        val wrapper = wrapper().apply { createKey() }
        val wrapped = wrapper.wrap(ByteArray(32), associatedData)

        assertFailsWith<AEADBadTagException> { wrapper.unwrap(wrapped, "aegis/other".toByteArray()) }
    }

    @Test
    fun preferringStrongBoxStillCreatesAWorkingKeyOnEveryDevice() {
        val wrapper = wrapper(preferStrongBox = true)

        wrapper.createKey()

        val secret = ByteArray(32) { 7 }
        assertContentEquals(secret, wrapper.unwrap(wrapper.wrap(secret, associatedData), associatedData))
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
            assertEquals(listOf("SECURITY.STRONGBOX_KEY_FAILED"), sink.records.map { it.event })
        }
    }

    @Test
    fun storeKeepsTheSameSecretAcrossInstances() =
        runTest {
            val created = assertIs<SecretResult.Available>(store().getOrCreate(SecretId.APP_DATABASE))
            val read = assertIs<SecretResult.Available>(store().getOrCreate(SecretId.APP_DATABASE))

            assertTrue(created.isNew)
            assertFalse(read.isNew)
            assertContentEquals(created.secret.copyBytes(), read.secret.copyBytes())
        }

    @Test
    fun storeReportsALostKeyAndKeepsTheFile() =
        runTest {
            store().getOrCreate(SecretId.APP_DATABASE)
            val file = File(directory, SecretId.APP_DATABASE.fileName)
            val before = file.readBytes()
            AndroidKeystore.deleteKey(TEST_ALIAS)

            val result = store().getOrCreate(SecretId.APP_DATABASE)

            assertEquals(SecretFailure.KEY_LOST, assertIs<SecretResult.Unavailable>(result).failure)
            assertContentEquals(before, file.readBytes())
        }

    private companion object {
        const val TEST_ALIAS = "aegis_secret_wrapping_test"
    }
}
