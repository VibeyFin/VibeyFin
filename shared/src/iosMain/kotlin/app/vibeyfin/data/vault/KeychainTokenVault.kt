package app.vibeyfin.data.vault

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import kotlin.uuid.Uuid

/**
 * Keeps each token as its own Keychain item and hands back a handle to it as the blob.
 *
 * Items are readable after the first unlock and never leave the device (no iCloud sync, not in
 * backups). A blob whose item is gone, e.g. after restoring onto a new phone, reads as null.
 */
@OptIn(ExperimentalForeignApi::class)
class KeychainTokenVault(
    private val service: String = DEFAULT_SERVICE,
) : TokenVault {

    override fun seal(token: String): ByteArray {
        val handle = Uuid.random().toString()
        val status = withItemQuery(handle) { query ->
            val data = token.encodeToByteArray().toCFData()
            CFDictionaryAddValue(query, kSecValueData, data)
            CFRelease(data)
            CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            SecItemAdd(query, null)
        }
        check(status == errSecSuccess) { "Keychain refused the token (OSStatus $status)." }
        return handle.encodeToByteArray()
    }

    override fun open(sealed: ByteArray): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = withItemQuery(sealed.decodeToString()) { query ->
            CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
            SecItemCopyMatching(query, result.ptr)
        }
        val found = result.value
        if (status != errSecSuccess || found == null) return@memScoped null
        val data: CFDataRef = found.reinterpret()
        try {
            CFDataGetBytePtr(data)?.readBytes(CFDataGetLength(data).toInt())?.decodeToString()
        } finally {
            CFRelease(data)
        }
    }

    override fun discard(sealed: ByteArray) {
        withItemQuery(sealed.decodeToString()) { query -> SecItemDelete(query) }
    }

    // Builds the query that identifies one token's item, runs [block] with it, and cleans up.
    private fun <T> withItemQuery(handle: String, block: (CFMutableDictionaryRef) -> T): T {
        val query = checkNotNull(
            CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                0,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr,
            ),
        )
        val serviceRef = CFStringCreateWithCString(kCFAllocatorDefault, service, kCFStringEncodingUTF8)
        val accountRef = CFStringCreateWithCString(kCFAllocatorDefault, handle, kCFStringEncodingUTF8)
        try {
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, serviceRef)
            CFDictionaryAddValue(query, kSecAttrAccount, accountRef)
            return block(query)
        } finally {
            CFRelease(accountRef)
            CFRelease(serviceRef)
            CFRelease(query)
        }
    }

    private fun ByteArray.toCFData() = usePinned { pinned ->
        checkNotNull(
            CFDataCreate(
                kCFAllocatorDefault,
                if (isEmpty()) null else pinned.addressOf(0).reinterpret<UByteVar>(),
                size.convert(),
            ),
        )
    }

    companion object {
        const val DEFAULT_SERVICE = "app.vibeyfin.tokens"
    }
}
