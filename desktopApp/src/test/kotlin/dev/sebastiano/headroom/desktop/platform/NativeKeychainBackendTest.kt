package dev.sebastiano.headroom.desktop.platform

import com.sun.jna.Memory
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeKeychainBackendTest {
    private val cf = FakeCoreFoundation()
    private val security = FakeSecurity(cf)
    private val backend = NativeKeychainBackend {
        KeychainFrameworks(security, cf, { cf.constant(it) }, { cf.constant(it.toString()) })
    }

    @Test
    fun `native read uses exact filters and UTF-8 byte lengths`() {
        security.readBytes = "fake-\u00e9\u0000-tail".toByteArray(Charsets.UTF_8)
        val service = "test-\u00e9\u0000-service"
        val key = "credential.\uD83D\uDD11\u0000-fake"
        val result = backend.read(service, key)
        assertContentEquals(security.readBytes, result.bytes)
        assertEquals(
            query(service, key) +
                mapOf("kSecReturnData" to "true", "kSecMatchLimit" to "kSecMatchLimitOne"),
            security.calls.single().query,
        )
        cf.assertAllReleased()
    }

    @Test
    fun `update adds a missing item and retries a duplicate race with a fresh query`() {
        security.updateStatuses.addAll(listOf(ITEM_NOT_FOUND, SUCCESS))
        security.addStatus = DUPLICATE_ITEM
        val bytes = "fake-\u00e9\u0000-tail".toByteArray(Charsets.UTF_8)
        assertEquals(SUCCESS, backend.write("test", "credential.fake", bytes))
        assertEquals(listOf("update", "add", "update"), security.calls.map { it.operation })
        val (initial, add, retry) = security.calls
        val values = mapOf("kSecValueData" to bytes.toList())
        assertEquals(query(), initial.query)
        assertEquals(values, initial.attributes)
        assertEquals(query() + values, add.query)
        assertNull(add.attributes)
        assertEquals(initial.queryPointer, add.queryPointer)
        assertNotEquals(initial.queryPointer, retry.queryPointer)
        assertEquals(query(), retry.query)
        assertEquals(values, retry.attributes)
        cf.assertAllReleased()
    }

    @Test
    fun `successful update changes only the data and never adds an item`() {
        security.updateStatuses.add(SUCCESS)
        val bytes = byteArrayOf()
        assertEquals(SUCCESS, backend.write("test", "credential.fake", bytes))
        val call = security.calls.single()
        assertEquals("update", call.operation)
        assertEquals(query(), call.query)
        assertEquals(mapOf("kSecValueData" to emptyList<Byte>()), call.attributes)
        cf.assertAllReleased()
    }

    @Test
    fun `missing update adds exact attributes and returns add failure`() {
        security.updateStatuses.add(ITEM_NOT_FOUND)
        security.addStatus = -25293
        val bytes = "fake-only".toByteArray(Charsets.UTF_8)
        assertEquals(-25293, backend.write("test", "credential.fake", bytes))
        assertEquals(listOf("update", "add"), security.calls.map { it.operation })
        assertEquals(
            query() + mapOf("kSecValueData" to bytes.toList()),
            security.calls.last().query,
        )
        cf.assertAllReleased()
    }

    @Test
    fun `failed update never attempts an add and releases all allocations`() {
        security.updateStatuses.add(-25293)
        assertEquals(-25293, backend.write("test", "credential.fake", byteArrayOf()))
        assertEquals(listOf("update"), security.calls.map { it.operation })
        cf.assertAllReleased()
    }

    @Test
    fun `read failure and invalid returned data release owned objects`() {
        security.readStatus = ITEM_NOT_FOUND
        assertEquals(ITEM_NOT_FOUND, backend.read("test", "credential.fake").status)
        cf.assertAllReleased()
        security.readStatus = SUCCESS
        cf.resultIsData = false
        assertFailsWith<IOException> { backend.read("test", "credential.fake") }
        cf.assertAllReleased()
    }

    @Test
    fun `null copy result and invalid lengths release query and returned data`() {
        security.nullReadResult = true
        assertFailsWith<IOException> { backend.read("test", "credential.fake") }
        cf.assertAllReleased()
        security.nullReadResult = false
        for (length in listOf(-1L, Int.MAX_VALUE.toLong() + 1)) {
            cf.reportedLength = length
            assertFailsWith<IOException> { backend.read("test", "credential.fake") }
            cf.assertAllReleased()
        }
    }

    @Test
    fun `null data pointer for nonempty result releases all allocations`() {
        cf.reportedLength = 1
        assertFailsWith<IOException> { backend.read("test", "credential.fake") }
        cf.assertAllReleased()
    }

    @Test
    fun `failed dictionary string and data allocations release earlier objects`() {
        // Query dictionary, service string, account string, attributes dictionary, then CFData.
        for (allocation in 1..5) {
            cf.failAllocationAt = cf.allocationAttempts + allocation
            assertFailsWith<IOException> { backend.write("test", "credential.fake", byteArrayOf()) }
            assertTrue(security.calls.isEmpty())
            cf.assertAllReleased()
        }
    }

    @Test
    fun `delete passes through failure with only exact identity filters`() {
        security.deleteStatus = -25308
        assertEquals(-25308, backend.delete("test", "credential.fake"))
        assertEquals("delete", security.calls.single().operation)
        assertEquals(query(), security.calls.single().query)
        cf.assertAllReleased()
    }

    private fun query(service: String = "test", key: String = "credential.fake"): Map<String, Any> =
        mapOf(
            "kSecClass" to "kSecClassGenericPassword",
            "kSecAttrService" to service,
            "kSecAttrAccount" to key,
            "kSecAttrSynchronizable" to "false",
        )

    private data class Call(
        val operation: String,
        val queryPointer: Pointer,
        val query: Map<String, Any>,
        val attributes: Map<String, Any>? = null,
    )

    private class FakeSecurity(private val cf: FakeCoreFoundation) : SecurityFramework {
        val updateStatuses = ArrayDeque<Int>()
        val calls = mutableListOf<Call>()
        var addStatus = SUCCESS
        var readStatus = SUCCESS
        var deleteStatus = SUCCESS
        var readBytes = byteArrayOf()
        var nullReadResult = false

        override fun SecItemCopyMatching(query: Pointer, result: PointerByReference): Int {
            calls.add(Call("read", query, cf.snapshot(query)))
            if (readStatus == SUCCESS && !nullReadResult) result.value = cf.result(readBytes)
            return readStatus
        }

        override fun SecItemAdd(attributes: Pointer, result: PointerByReference?): Int {
            assertNull(result)
            calls.add(Call("add", attributes, cf.snapshot(attributes)))
            return addStatus
        }

        override fun SecItemUpdate(query: Pointer, attributes: Pointer): Int {
            calls.add(Call("update", query, cf.snapshot(query), cf.snapshot(attributes)))
            return updateStatuses.removeFirst()
        }

        override fun SecItemDelete(query: Pointer): Int {
            calls.add(Call("delete", query, cf.snapshot(query)))
            return deleteStatus
        }
    }

    private class FakeCoreFoundation : CoreFoundationFramework {
        private var nextPointer = 1L
        private val allocated = mutableSetOf<Pointer>()
        private val released = mutableSetOf<Pointer>()
        private val constants = mutableMapOf<String, Pointer>()
        private val dictionaries = mutableMapOf<Pointer, MutableMap<Pointer, Pointer>>()
        private val values = mutableMapOf<Pointer, Any>()
        private var resultBytes = byteArrayOf()
        private var resultMemory: Memory? = null
        var resultIsData = true
        var reportedLength: Long? = null
        var allocationAttempts = 0
        var failAllocationAt: Int? = null

        fun constant(name: String): Pointer =
            constants.getOrPut(name) { Pointer(nextPointer++).also { values[it] = name } }

        fun snapshot(dictionary: Pointer): Map<String, Any> =
            dictionaries.getValue(dictionary).entries.associate { (key, value) ->
                values.getValue(key) as String to values.getValue(value)
            }

        private fun allocate(): Pointer? {
            allocationAttempts++
            return if (allocationAttempts == failAllocationAt) null
            else Pointer(nextPointer++).also { allocated.add(it) }
        }

        fun result(bytes: ByteArray): Pointer? {
            resultBytes = bytes.copyOf()
            if (bytes.isNotEmpty()) {
                resultMemory =
                    Memory(bytes.size.toLong()).also { it.write(0, bytes, 0, bytes.size) }
            }
            return allocate()
        }

        fun assertAllReleased() {
            assertEquals(allocated, released)
            resultMemory?.close()
            resultMemory = null
        }

        override fun CFDictionaryCreateMutable(
            allocator: Pointer?,
            capacity: NativeLong,
            keys: Pointer?,
            values: Pointer?,
        ): Pointer? {
            assertNull(allocator)
            assertEquals(0L, capacity.toLong())
            assertNull(keys)
            assertNull(values)
            return allocate()?.also { dictionaries[it] = mutableMapOf() }
        }

        override fun CFDictionarySetValue(dictionary: Pointer, key: Pointer, value: Pointer) {
            assertTrue(dictionary !in released)
            assertTrue(key !in released)
            assertTrue(value !in released)
            dictionaries.getValue(dictionary)[key] = value
        }

        override fun CFStringCreateWithBytes(
            allocator: Pointer?,
            bytes: ByteArray,
            length: NativeLong,
            encoding: Int,
            external: Byte,
        ): Pointer? {
            assertNull(allocator)
            assertEquals(bytes.size.toLong(), length.toLong())
            assertEquals(0x08000100, encoding)
            assertEquals(0.toByte(), external)
            return allocate()?.also { values[it] = bytes.toString(Charsets.UTF_8) }
        }

        override fun CFDataCreate(
            allocator: Pointer?,
            bytes: ByteArray,
            length: NativeLong,
        ): Pointer? {
            assertNull(allocator)
            assertEquals(bytes.size.toLong(), length.toLong())
            return allocate()?.also { values[it] = bytes.toList() }
        }

        override fun CFDataGetLength(data: Pointer): NativeLong =
            NativeLong(reportedLength ?: resultBytes.size.toLong())

        override fun CFDataGetBytePtr(data: Pointer): Pointer? = resultMemory

        override fun CFDataGetTypeID(): NativeLong = NativeLong(1)

        override fun CFGetTypeID(value: Pointer): NativeLong =
            NativeLong(if (resultIsData) 1 else 2)

        override fun CFRelease(value: Pointer) {
            assertTrue(allocated.contains(value))
            assertTrue(released.add(value), "Native allocation released twice")
        }
    }
}
