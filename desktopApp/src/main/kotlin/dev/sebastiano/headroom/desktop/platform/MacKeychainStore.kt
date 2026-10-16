package dev.sebastiano.headroom.desktop.platform

import dev.sebastiano.headroom.shared.SecureValueStore
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Generic-password items in the macOS login Keychain, never in preferences or process arguments.
 */
public class MacKeychainStore
internal constructor(
    private val backend: KeychainBackend,
    private val service: String = DEFAULT_SERVICE,
) : SecureValueStore {
    public constructor(service: String = DEFAULT_SERVICE) : this(NativeKeychainBackend(), service)

    override fun read(key: String): String? {
        val result =
            safely { backend.read(service, key) }
                ?: throw IOException("macOS Keychain is unavailable")
        if (result.status == ITEM_NOT_FOUND) return null
        if (result.status != SUCCESS) {
            throw IOException("Could not read macOS Keychain (OSStatus ${result.status})")
        }
        val bytes = result.bytes ?: throw IOException("macOS Keychain returned no data")
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            throw IOException("macOS Keychain returned invalid UTF-8")
        } finally {
            bytes.fill(0)
        }
    }

    override fun write(key: String, value: String): Boolean {
        val bytes = value.toByteArray(Charsets.UTF_8)
        return try {
            safely { backend.write(service, key, bytes) } == SUCCESS
        } finally {
            bytes.fill(0)
        }
    }

    override fun delete(key: String): Boolean = safely {
        backend.delete(service, key)
    }
        .let { it == SUCCESS || it == ITEM_NOT_FOUND }

    // Do not attach native exception causes: they may include account names or credential data.
    private fun <T> safely(operation: () -> T): T? =
        try {
            operation()
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        } catch (_: LinkageError) {
            null
        }

    private companion object {
        const val DEFAULT_SERVICE = "dev.sebastiano.headroom.desktop"
    }
}

internal const val SUCCESS = 0
internal const val ITEM_NOT_FOUND = -25300
internal const val DUPLICATE_ITEM = -25299

internal class KeychainReadResult(val status: Int, val bytes: ByteArray? = null)

/** Returning bytes transfers ownership to the caller, which wipes them after decoding. */
internal interface KeychainBackend {
    fun read(service: String, key: String): KeychainReadResult

    fun write(service: String, key: String, value: ByteArray): Int

    fun delete(service: String, key: String): Int
}
