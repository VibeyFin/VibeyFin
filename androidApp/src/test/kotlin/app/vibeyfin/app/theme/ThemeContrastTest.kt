package app.vibeyfin.app.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/** Fails when a theme, on either platform, has a text or border colour too faint on the grounds it is drawn on. */
class ThemeContrastTest {
    private class Rgba(val red: Double, val green: Double, val blue: Double, val alpha: Double)

    private class Pair(val foreground: String, val ground: String, val minimum: Double)

    private val tokens: JsonObject = Json.parseToJsonElement(File("../design/tokens.json").readText()).jsonObject
    private val platforms: List<String> = listOf("ios", "android")

    private val textOnEverySurface: List<String> = listOf("bg", "surface-1", "surface-2", "surface-3")
    private val pairs: List<Pair> = buildList {
        textOnEverySurface.forEach { ground ->
            add(Pair("ink", ground, TEXT))
            add(Pair("ink-muted", ground, TEXT))
            add(Pair("line-strong", ground, NON_TEXT))
        }

        listOf("bg", "surface-1", "surface-2").forEach { ground -> add(Pair("spark", ground, TEXT)) }
        listOf("bg", "surface-1").forEach { ground ->
            add(Pair("ink-subtle", ground, TEXT))
            add(Pair("danger", ground, TEXT))
        }

        add(Pair("on-action", "action", TEXT))
        add(Pair("on-spark", "spark", TEXT))
        add(Pair("on-spark-soft", "spark-soft", TEXT))
        add(Pair("on-tonal", "tonal", TEXT))
        add(Pair("focus", "bg", NON_TEXT))
    }

    private fun parse(value: String): Rgba {
        val trimmed = value.trim()

        if (trimmed.startsWith("#")) {
            val rgb = trimmed.drop(1).toInt(16)

            return Rgba(((rgb shr 16) and 0xFF) / 255.0, ((rgb shr 8) and 0xFF) / 255.0, (rgb and 0xFF) / 255.0, 1.0)
        }

        val channels = Regex("""rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)""").matchEntire(trimmed)!!.groupValues.drop(1)

        return Rgba(channels[0].toInt() / 255.0, channels[1].toInt() / 255.0, channels[2].toInt() / 255.0, channels[3].toDouble())
    }

    private fun over(foreground: Rgba, ground: Rgba): Rgba = Rgba(
        foreground.red * foreground.alpha + ground.red * (1 - foreground.alpha),
        foreground.green * foreground.alpha + ground.green * (1 - foreground.alpha),
        foreground.blue * foreground.alpha + ground.blue * (1 - foreground.alpha),
        1.0,
    )

    private fun linear(channel: Double): Double = if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

    private fun luminance(color: Rgba): Double = 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

    private fun ratio(foreground: Rgba, ground: Rgba): Double {
        val first = luminance(over(foreground, ground))
        val second = luminance(ground)

        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    private fun roleValues(theme: JsonObject, platform: String): Map<String, String> {
        val overrides = theme.getValue("overrides").jsonObject
        val base = tokens.getValue("color").jsonObject.getValue("tokens").jsonArray
            .map { it.jsonObject }
            .filter { it.getValue("value") is JsonObject }
            .associate { it.getValue("name").jsonPrimitive.content to it.getValue("value").jsonObject }

        return base.mapValues { (role, value) -> (overrides[role]?.jsonObject ?: value).getValue(platform).jsonPrimitive.content }
    }

    @Test
    fun everyThemeIsReadableOnEveryPlatform() {
        val failures = mutableListOf<String>()

        tokens.getValue("themes").jsonArray.map { it.jsonObject }.forEach { theme ->
            platforms.forEach { platform ->
                val values = roleValues(theme, platform)

                pairs.forEach { pair ->
                    val ground = parse(values.getValue(pair.ground))
                    val actual = ratio(parse(values.getValue(pair.foreground)), ground)

                    if (actual < pair.minimum) {
                        failures += "${theme.getValue("id").jsonPrimitive.content} / $platform: ${pair.foreground} on ${pair.ground} is ${"%.2f".format(actual)}:1, needs ${pair.minimum}:1"
                    }
                }
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private companion object {
        const val TEXT: Double = 4.5
        const val NON_TEXT: Double = 3.0
    }
}
