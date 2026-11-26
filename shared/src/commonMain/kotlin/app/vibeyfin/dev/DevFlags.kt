package app.vibeyfin.dev

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

enum class DevFlagKind { Toggle }

/** One developer option. The label is a literal on purpose: debug builds only, so it is never translated or shipped as a string. */
data class DevFlag(
    val id: String,
    val label: String,
    val kind: DevFlagKind,
    val persist: Boolean,
)

/** Every developer option; a new one is one more entry here. */
object DevFlagRegistry {
    val showSkeletons: DevFlag = DevFlag(id = "show_skeletons", label = "Show skeletons", kind = DevFlagKind.Toggle, persist = false)

    val all: List<DevFlag> = listOf(showSkeletons)
}

/** A toggle flag with its current value, for the settings tree. */
data class DevFlagToggle(val flag: DevFlag, val checked: Boolean)

/** The developer options' values, in memory. In a release build ([enabled] false) every flag reads off and cannot be set. */
class DevFlags(val enabled: Boolean) {
    private val states: Map<String, MutableStateFlow<Boolean>> = DevFlagRegistry.all.associate { it.id to MutableStateFlow(false) }

    fun state(flag: DevFlag): StateFlow<Boolean> = states.getValue(flag.id)

    fun set(flag: DevFlag, value: Boolean) {
        if (enabled) states.getValue(flag.id).value = value
    }

    fun set(id: String, value: Boolean) {
        DevFlagRegistry.all.firstOrNull { it.id == id }?.let { set(it, value) }
    }

    fun toggles(): List<DevFlagToggle> = DevFlagRegistry.all
        .filter { it.kind == DevFlagKind.Toggle }
        .map { DevFlagToggle(it, states.getValue(it.id).value) }
}

/** Closed while "Show skeletons" is on, so data requests wait and screens stay on their skeletons. Always open in a release build. */
class SkeletonGate(flags: DevFlags) {
    val held: StateFlow<Boolean> = flags.state(DevFlagRegistry.showSkeletons)

    suspend fun awaitOpen() {
        held.first { !it }
    }

    companion object {
        val AlwaysOpen: SkeletonGate = SkeletonGate(DevFlags(enabled = false))
    }
}
