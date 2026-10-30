package app.vibeyfin.ui.common

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlurHashTest {
    // Two hashes the Jellyfin demo server sent, with the pixels an independent double-precision decoder
    // (a straight transcription of the BlurHash spec) gives for them at these sizes.
    private val poster = "dJC%Z|bY-E?b0cM{m@M|ORInDjIB%M%f%M%3Vut7NYoM"
    private val backdrop = "WcJ[I,IU?bay-;~q_3IUM{ayIUt7ofM{j[xuIURjxuj[ofWBoft7"

    private fun BlurHashImage.pixel(x: Int, y: Int) = pixels[y * width + x]

    // Each channel within one step: the reference rounds in double precision, this in float.
    private fun assertPixel(expected: Int, actual: Int, where: String) {
        for (shift in listOf(16, 8, 0)) {
            val e = (expected shr shift) and 0xFF
            val a = (actual shr shift) and 0xFF
            assertTrue(abs(e - a) <= 1, "$where: expected ${expected.toString(16)}, was ${actual.toString(16)}")
        }
        assertEquals(0xFF, actual ushr 24, "$where is opaque")
    }

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun aTallTileGetsATallPicture() {
        val image = assertNotNull(BlurHash.decode(poster, aspectRatio = 2f / 3f))
        assertEquals(21, image.width)
        assertEquals(32, image.height)
        assertEquals(21 * 32, image.pixels.size)
        assertPixel(rgb(93, 95, 94), image.pixel(0, 0), "top left")
        assertPixel(rgb(45, 47, 27), image.pixel(20, 0), "top right")
        assertPixel(rgb(106, 103, 100), image.pixel(0, 31), "bottom left")
        assertPixel(rgb(113, 112, 114), image.pixel(20, 31), "bottom right")
        assertPixel(rgb(76, 65, 73), image.pixel(10, 16), "middle")
    }

    @Test
    fun aWideTileGetsAWidePicture() {
        val image = assertNotNull(BlurHash.decode(backdrop, aspectRatio = 16f / 9f))
        assertEquals(32, image.width)
        assertEquals(18, image.height)
        assertPixel(rgb(237, 237, 237), image.pixel(0, 0), "top left")
        assertPixel(rgb(214, 214, 214), image.pixel(31, 0), "top right")
        assertPixel(rgb(211, 211, 211), image.pixel(0, 17), "bottom left")
        assertPixel(rgb(155, 155, 155), image.pixel(31, 17), "bottom right")
        assertPixel(rgb(178, 178, 178), image.pixel(16, 9), "middle")
    }

    @Test
    fun aHashWithOnlyTheAverageColourIsOneFlatColour() {
        // Size flag 0 is a single component; "0" is no waves at all; then the colour as four base-83 digits.
        val hash = "00" + base83(0x2080E0, digits = 4)
        val image = assertNotNull(BlurHash.decode(hash))
        assertEquals(32, image.width)
        assertEquals(32, image.height)
        image.pixels.forEach { assertPixel(rgb(0x20, 0x80, 0xE0), it, "flat colour") }
    }

    @Test
    fun theLongSideIsAlways32AndTheShortSideNeverVanishes() {
        assertEquals(32 to 1, BlurHash.decode(backdrop, 100f)!!.let { it.width to it.height })
        assertEquals(1 to 32, BlurHash.decode(backdrop, 0.01f)!!.let { it.width to it.height })
    }

    @Test
    fun anUnusableRatioFallsBackToASquare() {
        for (ratio in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val image = assertNotNull(BlurHash.decode(poster, ratio), "ratio $ratio")
            assertEquals(32 to 32, image.width to image.height, "ratio $ratio")
        }
    }

    @Test
    fun somethingThatIsNotABlurHashIsNull() {
        assertNull(BlurHash.decode(""))
        assertNull(BlurHash.decode("L"))
        // The right alphabet but the wrong length for the size flag in its first character.
        assertNull(BlurHash.decode(poster.dropLast(2)))
        assertNull(BlurHash.decode(poster + "00"))
        // A character outside the base-83 alphabet, in the average colour and in a wave.
        assertNull(BlurHash.decode(poster.replaceRange(3, 4, " ")))
        assertNull(BlurHash.decode(poster.replaceRange(20, 21, "\"")))
        assertNull(BlurHash.decode("not a blurhash at all, just words"))
    }

    private fun base83(value: Int, digits: Int): String {
        val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"
        var rest = value
        val out = CharArray(digits)
        for (i in digits - 1 downTo 0) {
            out[i] = alphabet[rest % 83]
            rest /= 83
        }
        return out.concatToString()
    }
}
