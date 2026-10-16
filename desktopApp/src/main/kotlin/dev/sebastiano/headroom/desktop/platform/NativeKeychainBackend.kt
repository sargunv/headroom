package dev.sebastiano.headroom.desktop.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.io.IOException

internal class NativeKeychainBackend(
    loadFrameworks: () -> KeychainFrameworks = KeychainFrameworks::load
) : KeychainBackend {
    // Loading frameworks is deferred, so injected stores never load or touch the native Keychain.
    private val frameworks by lazy(loadFrameworks)

    override fun read(service: String, key: String): KeychainReadResult =
        frameworks.scope { scope ->
            val query = scope.query(service, key)
            scope.set(query, "kSecReturnData", frameworks.boolean(true))
            scope.set(query, "kSecMatchLimit", frameworks.securityConstant("kSecMatchLimitOne"))
            val result = PointerByReference()
            val status = frameworks.security.SecItemCopyMatching(query, result)
            val data = result.value?.let { scope.own(it) }
            if (status != SUCCESS) return@scope KeychainReadResult(status)
            if (
                data == null || frameworks.cf.CFGetTypeID(data) != frameworks.cf.CFDataGetTypeID()
            ) {
                throw IOException("macOS Keychain returned invalid data")
            }
            val length = frameworks.cf.CFDataGetLength(data).toLong()
            if (length !in 0..Int.MAX_VALUE.toLong()) {
                throw IOException("macOS Keychain returned an invalid data length")
            }
            val bytes =
                if (length == 0L) byteArrayOf()
                else {
                    val pointer =
                        frameworks.cf.CFDataGetBytePtr(data)
                            ?: throw IOException("macOS Keychain returned no bytes")
                    pointer.getByteArray(0, length.toInt())
                }
            KeychainReadResult(status, bytes)
        }

    override fun write(service: String, key: String, value: ByteArray): Int =
        frameworks.scope { scope ->
            val query = scope.query(service, key)
            val attributes = scope.dictionary()
            val data =
                scope.own(frameworks.cf.CFDataCreate(null, value, NativeLong(value.size.toLong())))
            scope.set(attributes, "kSecValueData", data)
            val status = frameworks.security.SecItemUpdate(query, attributes)
            if (status != ITEM_NOT_FOUND) return@scope status
            scope.set(query, "kSecValueData", data)
            val added = frameworks.security.SecItemAdd(query, null)
            // Another writer may have inserted the same item between the update and the add.
            if (added == DUPLICATE_ITEM)
                frameworks.security.SecItemUpdate(scope.query(service, key), attributes)
            else added
        }

    override fun delete(service: String, key: String): Int = frameworks.scope { scope ->
        frameworks.security.SecItemDelete(scope.query(service, key))
    }
}

internal class KeychainFrameworks(
    val security: SecurityFramework,
    val cf: CoreFoundationFramework,
    val securityConstant: (String) -> Pointer,
    val boolean: (Boolean) -> Pointer,
) {

    fun <T> scope(block: (CoreFoundationScope) -> T): T = CoreFoundationScope(this).use(block)

    companion object {
        private const val SECURITY = "/System/Library/Frameworks/Security.framework/Security"
        private const val CORE_FOUNDATION =
            "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation"

        fun load(): KeychainFrameworks {
            val securityLibrary = NativeLibrary.getInstance(SECURITY)
            val cfLibrary = NativeLibrary.getInstance(CORE_FOUNDATION)
            return KeychainFrameworks(
                Native.load(SECURITY, SecurityFramework::class.java),
                Native.load(CORE_FOUNDATION, CoreFoundationFramework::class.java),
                { name -> securityLibrary.getGlobalVariableAddress(name).getPointer(0) },
                { value ->
                    cfLibrary
                        .getGlobalVariableAddress(
                            if (value) "kCFBooleanTrue" else "kCFBooleanFalse"
                        )
                        .getPointer(0)
                },
            )
        }
    }
}

/**
 * Dictionaries borrow their entries; this scope releases every created object after the SecItem
 * call.
 */
internal class CoreFoundationScope(private val frameworks: KeychainFrameworks) : AutoCloseable {
    private val owned = mutableListOf<Pointer>()

    fun own(pointer: Pointer?): Pointer {
        if (pointer == null) throw IOException("Could not allocate macOS Keychain data")
        owned.add(pointer)
        return pointer
    }

    fun dictionary(): Pointer =
        own(frameworks.cf.CFDictionaryCreateMutable(null, NativeLong(0), null, null))

    fun set(dictionary: Pointer, key: String, value: Pointer) {
        frameworks.cf.CFDictionarySetValue(dictionary, frameworks.securityConstant(key), value)
    }

    fun query(service: String, key: String): Pointer =
        dictionary().also { query ->
            set(query, "kSecClass", frameworks.securityConstant("kSecClassGenericPassword"))
            set(query, "kSecAttrService", string(service))
            set(query, "kSecAttrAccount", string(key))
            // Explicitly exclude iCloud-synchronizable items; these credentials stay on this Mac.
            set(query, "kSecAttrSynchronizable", frameworks.boolean(false))
        }

    private fun string(value: String): Pointer {
        val bytes = value.toByteArray(Charsets.UTF_8)
        return own(
            frameworks.cf.CFStringCreateWithBytes(
                null,
                bytes,
                NativeLong(bytes.size.toLong()),
                UTF8,
                0,
            )
        )
    }

    override fun close() {
        owned.asReversed().forEach(frameworks.cf::CFRelease)
    }

    private companion object {
        const val UTF8 = 0x08000100
    }
}

@Suppress("FunctionNaming") // JNA method names must exactly match the exported C symbols.
internal interface SecurityFramework : Library {
    fun SecItemCopyMatching(query: Pointer, result: PointerByReference): Int

    fun SecItemAdd(attributes: Pointer, result: PointerByReference?): Int

    fun SecItemUpdate(query: Pointer, attributes: Pointer): Int

    fun SecItemDelete(query: Pointer): Int
}

@Suppress("FunctionNaming") // JNA method names must exactly match the exported C symbols.
internal interface CoreFoundationFramework : Library {
    fun CFDictionaryCreateMutable(
        allocator: Pointer?,
        capacity: NativeLong,
        keys: Pointer?,
        values: Pointer?,
    ): Pointer?

    fun CFDictionarySetValue(dictionary: Pointer, key: Pointer, value: Pointer)

    fun CFStringCreateWithBytes(
        allocator: Pointer?,
        bytes: ByteArray,
        length: NativeLong,
        encoding: Int,
        external: Byte,
    ): Pointer?

    fun CFDataCreate(allocator: Pointer?, bytes: ByteArray, length: NativeLong): Pointer?

    fun CFDataGetLength(data: Pointer): NativeLong

    fun CFDataGetBytePtr(data: Pointer): Pointer?

    fun CFDataGetTypeID(): NativeLong

    fun CFGetTypeID(value: Pointer): NativeLong

    fun CFRelease(value: Pointer)
}
