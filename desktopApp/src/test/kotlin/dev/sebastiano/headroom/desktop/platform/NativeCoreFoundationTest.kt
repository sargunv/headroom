package dev.sebastiano.headroom.desktop.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

/** Exercises real CoreFoundation ABI bindings only, never Security or the user's Keychain. */
@EnabledOnOs(OS.MAC)
class NativeCoreFoundationTest {
    @Test
    fun `real CoreFoundation roundtrips explicit UTF-8 lengths and balances scope releases`() {
        val native = Native.load(CORE_FOUNDATION, CoreFoundationFramework::class.java)
        val inspect = Native.load(CORE_FOUNDATION, CoreFoundationInspection::class.java)
        val created = mutableListOf<Pointer>()
        val released = mutableListOf<Pointer>()
        val cf =
            object : CoreFoundationFramework by native {
                override fun CFRelease(value: Pointer) {
                    released.add(value)
                    native.CFRelease(value)
                }
            }
        // Constructing the dependency graph cannot load Security: every access is a test failure.
        val unusedSecurity =
            Proxy.newProxyInstance(
                SecurityFramework::class.java.classLoader,
                arrayOf(SecurityFramework::class.java),
            ) { _, _, _ ->
                error("Security must not be called by a CoreFoundation test")
            } as SecurityFramework
        val frameworks =
            KeychainFrameworks(
                unusedSecurity,
                cf,
                { error("Security constants must not be resolved") },
                { error("No Keychain flags are needed") },
            )
        try {
            frameworks.scope { scope ->
                val dictionary = scope.dictionary().also(created::add)
                for (text in listOf("", "fake-\u00e9-\u6f22\u5b57-\uD83D\uDD11\u0000-tail")) {
                    val bytes = text.toByteArray(Charsets.UTF_8)
                    val string =
                        scope
                            .own(
                                cf.CFStringCreateWithBytes(
                                    null,
                                    bytes,
                                    NativeLong(bytes.size.toLong()),
                                    UTF8,
                                    0,
                                )
                            )
                            .also(created::add)
                    val data =
                        scope
                            .own(cf.CFDataCreate(null, bytes, NativeLong(bytes.size.toLong())))
                            .also(created::add)
                    assertEquals(inspect.CFStringGetTypeID(), cf.CFGetTypeID(string))
                    assertEquals(text.length.toLong(), inspect.CFStringGetLength(string).toLong())
                    assertEquals(cf.CFDataGetTypeID(), cf.CFGetTypeID(data))
                    assertNotEquals(cf.CFDataGetTypeID(), cf.CFGetTypeID(string))
                    assertEquals(bytes.size.toLong(), cf.CFDataGetLength(data).toLong())
                    val roundtrip =
                        if (bytes.isEmpty()) byteArrayOf()
                        else assertNotNull(cf.CFDataGetBytePtr(data)).getByteArray(0, bytes.size)
                    assertContentEquals(bytes, roundtrip)
                    assertEquals(text, roundtrip.toString(Charsets.UTF_8))
                    cf.CFDictionarySetValue(dictionary, string, data)
                    assertEquals(data, inspect.CFDictionaryGetValue(dictionary, string))
                }
                assertEquals(inspect.CFDictionaryGetTypeID(), cf.CFGetTypeID(dictionary))
                assertEquals(2L, inspect.CFDictionaryGetCount(dictionary).toLong())
            }
        } finally {
            assertEquals(created.asReversed(), released)
        }
    }

    private companion object {
        const val CORE_FOUNDATION =
            "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation"
        const val UTF8 = 0x08000100
    }
}

@Suppress("FunctionNaming") // Test inspection methods must match CoreFoundation C symbols.
internal interface CoreFoundationInspection : Library {
    fun CFStringGetTypeID(): NativeLong

    fun CFStringGetLength(value: Pointer): NativeLong

    fun CFDictionaryGetTypeID(): NativeLong

    fun CFDictionaryGetCount(dictionary: Pointer): NativeLong

    fun CFDictionaryGetValue(dictionary: Pointer, key: Pointer): Pointer?
}
