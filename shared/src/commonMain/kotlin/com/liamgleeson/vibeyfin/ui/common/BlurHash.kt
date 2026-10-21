package com.liamgleeson.vibeyfin.ui.common

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

/** A BlurHash drawn out as a tiny picture: [pixels] are `0xAARRGGBB`, row by row, `width * height` of them. */
class BlurHashImage(val width: Int, val height: Int, val pixels: IntArray)

/**
 * Decodes the BlurHash strings the server sends with an item's artwork (`ImageBlurHashes`) into the
 * small blurred picture shown while the real one loads. Pure maths, so both apps share it; each one
 * only turns the pixels into a bitmap. The format is from https://blurha.sh.
 */
object BlurHash {
    /** The longest side of a decoded picture. It is stretched to the tile, so more pixels only cost time. */
    private const val MAX_SIDE = 32

    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

    /**
     * The picture for [hash] at the shape of the tile it will fill, [aspectRatio] being width over height.
     * Null when the string isn't a BlurHash, so a bad value from a server just shows the plain tile.
     */
    fun decode(hash: String, aspectRatio: Float = 1f): BlurHashImage? {
        if (hash.length < 6) return null
        val sizeFlag = base83(hash, 0, 1) ?: return null
        val componentsY = sizeFlag / 9 + 1
        val componentsX = sizeFlag % 9 + 1
        if (hash.length != 4 + 2 * componentsX * componentsY) return null

        val maximum = ((base83(hash, 1, 1) ?: return null) + 1) / 166f
        val colours = Array(componentsX * componentsY) { index ->
            val value = base83(hash, if (index == 0) 2 else 4 + index * 2, if (index == 0) 4 else 2) ?: return null
            if (index == 0) dc(value) else ac(value, maximum)
        }

        val ratio = if (aspectRatio > 0f && aspectRatio.isFinite()) aspectRatio else 1f
        val width = if (ratio >= 1f) MAX_SIDE else (MAX_SIDE * ratio).roundToInt().coerceAtLeast(1)
        val height = if (ratio >= 1f) (MAX_SIDE / ratio).roundToInt().coerceAtLeast(1) else MAX_SIDE

        val cosX = basis(componentsX, width)
        val cosY = basis(componentsY, height)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (j in 0 until componentsY) {
                    for (i in 0 until componentsX) {
                        val factor = cosX[i][x] * cosY[j][y]
                        val colour = colours[i + j * componentsX]
                        r += colour[0] * factor
                        g += colour[1] * factor
                        b += colour[2] * factor
                    }
                }
                pixels[y * width + x] = (0xFF shl 24) or (toSrgb(r) shl 16) or (toSrgb(g) shl 8) or toSrgb(b)
            }
        }
        return BlurHashImage(width, height, pixels)
    }

    /** `cos(pi * position * component / size)` for every component and pixel position along one axis. */
    private fun basis(components: Int, size: Int) = Array(components) { component ->
        FloatArray(size) { position -> cos(PI * component * position / size).toFloat() }
    }

    private fun base83(text: String, start: Int, length: Int): Int? {
        var value = 0
        for (i in start until start + length) {
            val digit = ALPHABET.indexOf(text[i])
            if (digit < 0) return null
            value = value * 83 + digit
        }
        return value
    }

    /** The average colour, stored as sRGB bytes. */
    private fun dc(value: Int) =
        floatArrayOf(toLinear(value shr 16), toLinear((value shr 8) and 255), toLinear(value and 255))

    /** One wave's strength per channel, stored as three base-19 digits, squared to keep the sign. */
    private fun ac(value: Int, maximum: Float) = floatArrayOf(
        signedSquare((value / (19 * 19) - 9) / 9f) * maximum,
        signedSquare((value / 19 % 19 - 9) / 9f) * maximum,
        signedSquare((value % 19 - 9) / 9f) * maximum,
    )

    private fun signedSquare(value: Float) = if (value < 0f) -(value * value) else value * value

    private fun toLinear(byte: Int): Float {
        val v = byte / 255f
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun toSrgb(linear: Float): Int {
        val v = linear.coerceIn(0f, 1f)
        val srgb = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
        return (srgb * 255f + 0.5f).toInt().coerceIn(0, 255)
    }
}
