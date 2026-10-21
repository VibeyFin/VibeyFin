package app.vibeyfin.ios

import app.vibeyfin.ui.common.BlurHash
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.UIKit.UIImage

/**
 * The BlurHash of an artwork as a `UIImage`, for SwiftUI to stretch over the tile while the real picture
 * loads. The decoding is shared ([BlurHash]); this only hands the pixels to CoreGraphics.
 */
@OptIn(ExperimentalForeignApi::class)
object BlurHashImages {
    /** The picture for [hash] at the shape of its tile (width over height), or null when it isn't a BlurHash. */
    fun image(hash: String, aspectRatio: Float): UIImage? {
        val decoded = BlurHash.decode(hash, aspectRatio) ?: return null
        val width = decoded.width
        val height = decoded.height
        // RGBA bytes in reading order; the fourth is skipped (`NoneSkipLast`), the picture is opaque.
        val bytes = ByteArray(width * height * 4)
        decoded.pixels.forEachIndexed { index, argb ->
            bytes[index * 4] = (argb shr 16).toByte()
            bytes[index * 4 + 1] = (argb shr 8).toByte()
            bytes[index * 4 + 2] = argb.toByte()
            bytes[index * 4 + 3] = -1
        }
        val colourSpace = CGColorSpaceCreateDeviceRGB()
        try {
            return bytes.usePinned { pinned ->
                val context = CGBitmapContextCreate(
                    data = pinned.addressOf(0),
                    width = width.toULong(),
                    height = height.toULong(),
                    bitsPerComponent = 8u,
                    bytesPerRow = (width * 4).toULong(),
                    space = colourSpace,
                    bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value,
                ) ?: return@usePinned null
                // The image copies the pixels, so it outlives the pinned array.
                val cgImage = CGBitmapContextCreateImage(context)
                CGContextRelease(context)
                cgImage?.let { UIImage.imageWithCGImage(it).also { _ -> CGImageRelease(it) } }
            }
        } finally {
            CGColorSpaceRelease(colourSpace)
        }
    }
}
