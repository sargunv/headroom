package dev.sebastiano.headroom.desktop.platform

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacKeychainStoreTest {
    private val backend = FakeKeychainBackend()
    private val store = MacKeychainStore(backend, "test.headroom")

    @Test
    fun `reads and writes exact UTF-8 including embedded null bytes`() {
        val value = "fake-token-\u00e9\uD83D\uDD11\u0000-tail"
        backend.readResult = KeychainReadResult(0, value.toByteArray(Charsets.UTF_8))

        assertEquals(value, store.read("credential.fake"))
        assertTrue(store.write("credential.fake", value))
        assertContentEquals(value.toByteArray(Charsets.UTF_8), backend.written)
        assertEquals("test.headroom", backend.service)
        assertEquals("credential.fake", backend.key)
    }

    @Test
    fun `only item not found is a missing credential`() {
        backend.readResult = KeychainReadResult(-25300)
        assertNull(store.read("credential.fake"))
        for (status in listOf(-25291, -25293, -25308, -50)) {
            backend.readResult = KeychainReadResult(status)
            val failure = assertFailsWith<IOException> { store.read("credential.fake") }
            assertTrue(failure.message.orEmpty().contains(status.toString()))
            assertFalse(failure.message.orEmpty().contains("credential.fake"))
        }
    }

    @Test
    fun `invalid UTF-8 and missing result bytes are read errors`() {
        backend.readResult = KeychainReadResult(0, byteArrayOf(0xc3.toByte(), 0x28))
        assertFailsWith<IOException> { store.read("credential.fake") }
        backend.readResult = KeychainReadResult(0)
        assertFailsWith<IOException> { store.read("credential.fake") }
        backend.readResult = KeychainReadResult(0, byteArrayOf())
        assertEquals("", store.read("credential.fake"))
    }

    @Test
    fun `write and delete failures return false and absent deletion succeeds`() {
        for (status in listOf(-25291, -25293, -25308, -50)) {
            backend.writeStatus = status
            backend.deleteStatus = status
            assertFalse(store.write("credential.fake", "fake-secret"))
            assertFalse(store.delete("credential.fake"))
        }
        backend.deleteStatus = -25300
        assertTrue(store.delete("credential.fake"))
        backend.deleteStatus = 0
        assertTrue(store.delete("credential.fake"))
    }

    @Test
    fun `native backend failures become safe errors without leaking details`() {
        backend.failure = IOException("fake-secret must not appear")
        val failure = assertFailsWith<IOException> { store.read("credential.fake") }
        assertFalse(failure.toString().contains("fake-secret"))
        assertNull(failure.cause)
        assertFalse(store.write("credential.fake", "fake-secret"))
        assertFalse(store.delete("credential.fake"))
    }

    private class FakeKeychainBackend : KeychainBackend {
        var readResult = KeychainReadResult(-25300)
        var writeStatus = 0
        var deleteStatus = 0
        var failure: IOException? = null
        var service: String? = null
        var key: String? = null
        var written: ByteArray? = null

        override fun read(service: String, key: String): KeychainReadResult {
            failure?.let { throw it }
            this.service = service
            this.key = key
            return readResult
        }

        override fun write(service: String, key: String, value: ByteArray): Int {
            failure?.let { throw it }
            this.service = service
            this.key = key
            written = value.copyOf()
            return writeStatus
        }

        override fun delete(service: String, key: String): Int {
            failure?.let { throw it }
            return deleteStatus
        }
    }
}
