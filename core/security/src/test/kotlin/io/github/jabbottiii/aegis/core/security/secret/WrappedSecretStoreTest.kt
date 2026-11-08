package io.github.jabbottiii.aegis.core.security.secret

import io.github.jabbottiii.aegis.core.common.logging.LogLevel
import io.github.jabbottiii.aegis.core.common.logging.LogPolicy
import io.github.jabbottiii.aegis.core.common.logging.PolicyLogger
import io.github.jabbottiii.aegis.testing.common.RecordingLogSink
import io.github.jabbottiii.aegis.testing.common.TestAegisDispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.ProviderException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WrappedSecretStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val sink = RecordingLogSink()
    private val logger = PolicyLogger(sink, LogPolicy.DEBUG)
    private val wrapper = SoftwareTestKeyWrapper()
    private val synced = mutableListOf<File>()
    private val directory by lazy { File(folder.root, "secrets") }

    private fun TestScope.store(keyWrapper: KeyWrapper = wrapper) =
        WrappedSecretStore(
            directory = directory,
            wrapper = keyWrapper,
            dispatchers = TestAegisDispatchers(this),
            logger = logger,
            syncDirectory = { synced += it },
        )

    private fun secretFile(id: SecretId = SecretId.APP_DATABASE) = File(directory, id.fileName)

    private fun SecretResult.bytes(): ByteArray = assertIs<SecretResult.Available>(this).secret.copyBytes()

    private fun SecretResult.failure(): SecretFailure = assertIs<SecretResult.Unavailable>(this).failure

    private fun events() = sink.records.map { it.event }

    @Test
    fun `the first call creates a 32-byte secret and later calls return the same bytes`() =
        runTest {
            val store = store()

            val first = store.getOrCreate(SecretId.APP_DATABASE)
            val second = store.getOrCreate(SecretId.APP_DATABASE)

            assertTrue(assertIs<SecretResult.Available>(first).isNew)
            assertFalse(assertIs<SecretResult.Available>(second).isNew)
            assertEquals(32, first.bytes().size)
            assertContentEquals(first.bytes(), second.bytes())
            assertEquals(1, wrapper.keysCreated)
            assertEquals(listOf(directory), synced)
            assertEquals(listOf("SECURITY.SECRET_CREATED"), events())
        }

    @Test
    fun `a new store on the same directory, like another process, reads the same secret`() =
        runTest {
            val created = store().getOrCreate(SecretId.APP_DATABASE).bytes()

            val read = store().getOrCreate(SecretId.APP_DATABASE)

            assertFalse(assertIs<SecretResult.Available>(read).isNew)
            assertContentEquals(created, read.bytes())
        }

    @Test
    fun `secrets for different IDs differ and share one wrapping key`() =
        runTest {
            val store = store()

            val app = store.getOrCreate(SecretId.APP_DATABASE).bytes()
            val shield = store.getOrCreate(SecretId.SHIELD_DATABASE).bytes()

            assertFalse(app.contentEquals(shield))
            assertEquals(1, wrapper.keysCreated)
        }

    @Test
    fun `concurrent first calls create exactly one secret`() =
        runTest {
            val stores = List(4) { store() }

            val results = stores.map { async { it.getOrCreate(SecretId.APP_DATABASE) } }.awaitAll()

            assertEquals(1, results.count { assertIs<SecretResult.Available>(it).isNew })
            results.forEach { assertContentEquals(results.first().bytes(), it.bytes()) }
            assertEquals(1, wrapper.keysCreated)
        }

    @Test
    fun `the stored file holds the format tag and ciphertext, never the secret itself`() =
        runTest {
            val secret = store().getOrCreate(SecretId.APP_DATABASE).bytes()

            val stored = secretFile().readBytes()

            assertContentEquals(WrappedSecretStore.FORMAT_TAG, stored.copyOfRange(0, 4))
            assertFalse(stored.toList().windowed(secret.size).any { it == secret.toList() })
        }

    @Test
    fun `a lost wrapping key is reported, and the stored secret is left in place`() =
        runTest {
            store().getOrCreate(SecretId.APP_DATABASE)
            val before = secretFile().readBytes()
            wrapper.forgetKey()

            val result = store().getOrCreate(SecretId.APP_DATABASE)

            assertEquals(SecretFailure.KEY_LOST, result.failure())
            assertContentEquals(before, secretFile().readBytes())
            assertEquals(1, wrapper.keysCreated)
            assertEquals(LogLevel.ERROR, sink.records.last().level)
            assertEquals("SECURITY.SECRET_KEY_LOST", sink.records.last().event)
        }

    @Test
    fun `while a secret is stored, a missing key is never replaced, even for a new ID`() =
        runTest {
            store().getOrCreate(SecretId.APP_DATABASE)
            wrapper.forgetKey()

            val result = store().getOrCreate(SecretId.SHIELD_DATABASE)

            assertEquals(SecretFailure.KEY_LOST, result.failure())
            assertEquals(1, wrapper.keysCreated)
            assertFalse(secretFile(SecretId.SHIELD_DATABASE).exists())
        }

    @Test
    fun `with nothing stored, a missing key is simply created again`() =
        runTest {
            wrapper.corruptWraps = true
            store().getOrCreate(SecretId.APP_DATABASE)
            wrapper.corruptWraps = false
            wrapper.forgetKey()

            val result = store().getOrCreate(SecretId.APP_DATABASE)

            assertTrue(assertIs<SecretResult.Available>(result).isNew)
            assertEquals(2, wrapper.keysCreated)
        }

    @Test
    fun `a tampered secret is unreadable and isn't replaced`() =
        runTest {
            store().getOrCreate(SecretId.APP_DATABASE)
            val tampered = secretFile().readBytes().also { it[it.size - 1] = (it.last().toInt() xor 1).toByte() }
            secretFile().writeBytes(tampered)

            val result = store().getOrCreate(SecretId.APP_DATABASE)

            assertEquals(SecretFailure.UNREADABLE, result.failure())
            assertContentEquals(tampered, secretFile().readBytes())
            assertEquals("SECURITY.SECRET_UNREADABLE", events().last())
        }

    @Test
    fun `one secret's file can't stand in for another's`() =
        runTest {
            store().getOrCreate(SecretId.APP_DATABASE)
            secretFile().copyTo(secretFile(SecretId.SHIELD_DATABASE))

            val result = store().getOrCreate(SecretId.SHIELD_DATABASE)

            assertEquals(SecretFailure.UNREADABLE, result.failure())
        }

    @Test
    fun `malformed files are unreadable without asking the key wrapper`() =
        runTest {
            val malformed =
                listOf(
                    ByteArray(0),
                    WrappedSecretStore.FORMAT_TAG,
                    WrappedSecretStore.FORMAT_TAG + ByteArray(27),
                    WrappedSecretStore.FORMAT_TAG + ByteArray(59),
                    WrappedSecretStore.FORMAT_TAG + ByteArray(61),
                    byteArrayOf(0x41, 0x47, 0x53, 0x02) + ByteArray(60),
                    WrappedSecretStore.FORMAT_TAG + ByteArray(4096),
                )
            directory.mkdirs()

            malformed.forEach { contents ->
                secretFile().writeBytes(contents)
                assertEquals(SecretFailure.UNREADABLE, store().getOrCreate(SecretId.APP_DATABASE).failure())
            }
            assertEquals(0, wrapper.unwrapCalls)
        }

    @Test
    fun `a wrapped secret of the wrong length is unreadable`() =
        runTest {
            wrapper.createKey()
            directory.mkdirs()
            val shortSecret = wrapper.wrap(ByteArray(16), WrappedSecretStore.associatedData(SecretId.APP_DATABASE))
            secretFile().writeBytes(WrappedSecretStore.FORMAT_TAG + shortSecret)

            assertEquals(SecretFailure.UNREADABLE, store().getOrCreate(SecretId.APP_DATABASE).failure())
        }

    @Test
    fun `a ciphertext the key wrapper rejects as malformed is unreadable`() =
        runTest {
            store().getOrCreate(SecretId.APP_DATABASE)
            wrapper.unwrapFailure = BadPaddingException("test: ciphertext too short")

            assertEquals(SecretFailure.UNREADABLE, store().getOrCreate(SecretId.APP_DATABASE).failure())
        }

    @Test
    fun `a Keystore failure while checking for the key is transient and creates nothing`() =
        runTest {
            store().getOrCreate(SecretId.APP_DATABASE)
            wrapper.hasKeyFailure = ProviderException("test: keystore busy")

            assertEquals(SecretFailure.KEYSTORE_ERROR, store().getOrCreate(SecretId.SHIELD_DATABASE).failure())
            assertEquals(1, wrapper.keysCreated)
            assertFalse(secretFile(SecretId.SHIELD_DATABASE).exists())
        }

    @Test
    fun `a Keystore failure while creating the key leaves nothing behind`() =
        runTest {
            wrapper.createFailure = ProviderException("test: keystore unavailable")

            val result = store().getOrCreate(SecretId.APP_DATABASE)

            assertEquals(SecretFailure.KEYSTORE_ERROR, result.failure())
            assertFalse(secretFile().exists())
            assertEquals("SECURITY.SECRET_KEYSTORE_FAILED", events().last())
            assertEquals(ProviderException::class.java.name, sink.records.last().errorType)
        }

    @Test
    fun `a key that can't decrypt what it encrypted is caught before the secret is stored`() =
        runTest {
            wrapper.unwrapReturnsZeros = true
            assertEquals(SecretFailure.KEYSTORE_ERROR, store().getOrCreate(SecretId.APP_DATABASE).failure())
            assertFalse(secretFile().exists())

            wrapper.unwrapReturnsZeros = false
            wrapper.corruptWraps = true
            assertEquals(SecretFailure.KEYSTORE_ERROR, store().getOrCreate(SecretId.APP_DATABASE).failure())
            assertFalse(secretFile().exists())
        }

    @Test
    fun `after a transient Keystore failure, the next call creates the secret`() =
        runTest {
            wrapper.createFailure = ProviderException("test: keystore busy")
            store().getOrCreate(SecretId.APP_DATABASE)
            wrapper.createFailure = null

            val result = store().getOrCreate(SecretId.APP_DATABASE)

            assertTrue(assertIs<SecretResult.Available>(result).isNew)
        }

    @Test
    fun `a directory that can't be created is a storage error`() =
        runTest {
            directory.writeText("not a directory")

            assertEquals(SecretFailure.STORAGE_ERROR, store().getOrCreate(SecretId.APP_DATABASE).failure())
            assertEquals("SECURITY.SECRET_STORAGE_FAILED", events().last())
        }

    @Test
    fun `a failed directory sync withholds the new secret, and the next call returns it`() =
        runTest {
            val unsynced =
                WrappedSecretStore(
                    directory = directory,
                    wrapper = wrapper,
                    dispatchers = TestAegisDispatchers(this),
                    logger = logger,
                    syncDirectory = { throw java.io.IOException("test: fsync failed") },
                )

            val first = unsynced.getOrCreate(SecretId.APP_DATABASE)
            val second = store().getOrCreate(SecretId.APP_DATABASE)

            assertEquals(SecretFailure.STORAGE_ERROR, first.failure())
            assertEquals("SECURITY.SECRET_STORAGE_FAILED", events().single())
            assertFalse(assertIs<SecretResult.Available>(second).isNew)
            assertEquals(1, wrapper.keysCreated)
        }

    @Test
    fun `a leftover temporary file from an interrupted write doesn't matter`() =
        runTest {
            directory.mkdirs()
            File(directory, "${SecretId.APP_DATABASE.fileName}.tmp").writeText("partial")

            val created = store().getOrCreate(SecretId.APP_DATABASE)

            assertTrue(assertIs<SecretResult.Available>(created).isNew)
            assertContentEquals(created.bytes(), store().getOrCreate(SecretId.APP_DATABASE).bytes())
        }

    @Test
    fun `secrets don't show their bytes and can be wiped`() =
        runTest {
            val secret = assertIs<SecretResult.Available>(store().getOrCreate(SecretId.APP_DATABASE)).secret

            assertEquals("Secret(32 bytes)", secret.toString())
            val copy = secret.copyBytes()
            secret.wipe()
            assertTrue(secret.copyBytes().all { it == 0.toByte() })
            assertFalse(copy.all { it == 0.toByte() })
        }

    @Test
    fun `nothing logged carries secret material`() =
        runTest {
            wrapper.corruptWraps = true
            store().getOrCreate(SecretId.APP_DATABASE)
            wrapper.corruptWraps = false
            store().getOrCreate(SecretId.APP_DATABASE)

            sink.records.forEach { record ->
                assertTrue(record.values.isEmpty())
                assertTrue(record.event.startsWith("SECURITY."))
            }
        }
}

/**
 * A software AES-256-GCM key in place of the Android Keystore, so that the store's logic runs on
 * the JVM. It's generated per test run (`.claude/rules/intel-pipeline.md`, "Tests").
 */
private class SoftwareTestKeyWrapper : KeyWrapper {
    private var testKey: SecretKey? = null
    var keysCreated = 0
    var unwrapCalls = 0
    var createFailure: RuntimeException? = null
    var hasKeyFailure: RuntimeException? = null
    var unwrapFailure: GeneralSecurityException? = null
    var corruptWraps = false
    var unwrapReturnsZeros = false

    fun forgetKey() {
        testKey = null
    }

    override fun hasKey(): Boolean {
        hasKeyFailure?.let { throw it }
        return testKey != null
    }

    override fun createKey() {
        createFailure?.let { throw it }
        testKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        keysCreated++
    }

    override fun wrap(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(associatedData)
        val wrapped = cipher.iv + cipher.doFinal(plaintext)
        if (corruptWraps) wrapped[wrapped.size - 1] = (wrapped.last().toInt() xor 1).toByte()
        return wrapped
    }

    override fun unwrap(
        ciphertext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        unwrapCalls++
        unwrapFailure?.let { throw it }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, ciphertext, 0, 12))
        cipher.updateAAD(associatedData)
        val plaintext = cipher.doFinal(ciphertext, 12, ciphertext.size - 12)
        return if (unwrapReturnsZeros) ByteArray(plaintext.size) else plaintext
    }

    @Throws(GeneralSecurityException::class)
    private fun key(): SecretKey = testKey ?: throw InvalidKeyException("test: no key")
}
