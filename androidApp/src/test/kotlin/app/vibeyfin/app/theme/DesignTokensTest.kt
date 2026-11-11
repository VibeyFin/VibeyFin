package app.vibeyfin.app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Fails when the Kotlin tokens drift from design/tokens.json (the `android` platform column of every theme). */
class DesignTokensTest {
    private val tokens: JsonObject =
        Json.parseToJsonElement(File("../design/tokens.json").readText()).jsonObject

    private fun family(name: String) = tokens.getValue(name).jsonObject.getValue("tokens").jsonArray
        .map { it.jsonObject }
        .associateBy { it.getValue("name").jsonPrimitive.content }

    // glass is a preview stand-in (Android never uses glass); positive is an alias of ink
    private val notModelled = setOf("glass", "positive")

    private fun argb(value: String): Int {
        val v = value.trim()

        if (v.startsWith("#")) return (0xFF000000L or v.drop(1).toLong(16)).toInt()

        val channels = Regex("""rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)""")
            .matchEntire(v)!!.groupValues.drop(1)

        return Color(channels[0].toInt() / 255f, channels[1].toInt() / 255f, channels[2].toInt() / 255f, channels[3].toFloat()).toArgb()
    }

    @Test
    fun everyThemeHoldsEveryAndroidColorRole() {
        val json = family("color").filterKeys { it !in notModelled }
        val themes = tokens.getValue("themes").jsonArray.map { it.jsonObject }

        assertEquals(themes.map { it.getValue("id").jsonPrimitive.content }.toSet(), ThemePalettes.all.keys, "theme ids differ from design/tokens.json")
        themes.forEach { theme ->
            val id = theme.getValue("id").jsonPrimitive.content
            val overrides = theme.getValue("overrides").jsonObject
            val actual = ThemePalettes.all.getValue(id).byRole

            assertEquals(json.keys, actual.keys, "role names of $id differ from design/tokens.json")
            json.forEach { (role, token) ->
                val value = (overrides[role] ?: token.getValue("value")).jsonObject.getValue("android").jsonPrimitive.content

                assertEquals(argb(value), actual.getValue(role).toArgb(), "color $role of $id drifted, run ./gradlew generateThemes")
            }
        }
    }

    private fun px(token: Map<String, JsonObject>, name: String): Dp = token.getValue(name).getValue("value").jsonPrimitive.content.removeSuffix("px").toFloat().dp

    @Test
    fun spacingMatches() {
        val json = family("spacing")
        val actual = VibeyFinTokens.Space

        assertEquals(px(json, "space-1"), actual.s1)
        assertEquals(px(json, "space-2"), actual.s2)
        assertEquals(px(json, "space-3"), actual.s3)
        assertEquals(px(json, "space-4"), actual.s4)
        assertEquals(px(json, "space-5"), actual.s5)
        assertEquals(px(json, "space-6"), actual.s6)
        assertEquals(px(json, "space-7"), actual.s7)
    }

    @Test
    fun radiiAndSizesMatch() {
        val radius = family("radius")

        assertEquals(px(radius, "radius-artwork"), VibeyFinTokens.Radius.artwork)
        assertEquals(px(radius, "radius-m3-xs"), VibeyFinTokens.Radius.m3Xs)
        assertEquals(px(radius, "radius-m3-md"), VibeyFinTokens.Radius.m3Md)
        assertEquals(px(radius, "radius-m3-xl"), VibeyFinTokens.Radius.m3Xl)

        val size = family("size")

        assertEquals(px(size, "touch-android"), VibeyFinTokens.Size.touch)
        assertEquals(px(size, "button-android"), VibeyFinTokens.Size.button)
        assertEquals(px(size, "button-android-lg"), VibeyFinTokens.Size.buttonLarge)
        assertEquals(px(size, "poster-w"), VibeyFinTokens.Size.posterWidth)
        assertEquals(px(size, "thumb-w"), VibeyFinTokens.Size.thumbWidth)
    }

    @Test
    fun durationsMatch() {
        val json = family("duration")

        fun ms(name: String) = json.getValue(name).getValue("value").jsonPrimitive.content.removeSuffix("ms").toInt()
        assertEquals(ms("dur-base"), VibeyFinTokens.Motion.BASE_MS)
        assertEquals(ms("dur-hero"), VibeyFinTokens.Motion.HERO_MS)
    }

    @Test
    fun theAvatarListHoldsTheSixTokensInOrder() {
        val colors = VibeyFinTokens.Colors

        assertEquals(listOf(colors.avatar1, colors.avatar2, colors.avatar3, colors.avatar4, colors.avatar5, colors.avatar6), colors.avatars)
    }

    @Test
    fun anUnknownThemeIdFallsBackToTheDefaultTheme() {
        assertEquals(ThemePalettes.all.getValue(ThemePalettes.DEFAULT_ID), ThemePalettes.byId("no-such-theme"))
        assertEquals(ThemePalettes.all.getValue(ThemePalettes.DEFAULT_ID), ThemePalettes.byId(null))
    }

    @Test
    fun theActionAndTheSparkKeepTheirBrandValues() {
        assertEquals(0xFFFFFFFF.toInt(), VibeyFinTokens.Colors.action.toArgb())
        assertEquals(0xFFE07A45.toInt(), VibeyFinTokens.Colors.spark.toArgb())
    }
}
