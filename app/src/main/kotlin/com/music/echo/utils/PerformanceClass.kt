package iad1tya.echo.music.utils

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import iad1tya.echo.music.constants.AlbumCanvasEnabledKey
import iad1tya.echo.music.constants.CanvasThumbnailAnimationKey
import iad1tya.echo.music.constants.HighPerformanceModeKey
import iad1tya.echo.music.constants.LiquidGlassGlobalEnabledKey
import iad1tya.echo.music.constants.LiquidGlassInteractiveKey

/**
 * Registry row 898 (plan phase 7): the three performance classes the owner sees in Ajustes ▸ Rendimiento.
 *
 * Nothing new is measured: the class is the existing [DeviceCapabilities] tier (RAM, cores, the OEM media performance
 * class, the low-RAM flag) under a name — LOW → [ENTRY], MID → [BALANCED], HIGH → [PREMIUM].
 */
enum class PerformanceClass { ENTRY, BALANCED, PREMIUM }

/** What the user picked in Ajustes ▸ Rendimiento. [AUTO] (the default) follows the hardware. */
enum class PerformanceClassChoice { AUTO, ENTRY, BALANCED, PREMIUM }

/**
 * The settings a class decides. Only switches that ALREADY exist and that the app already set by tier once:
 *  · [highPerformanceMode] — `applyHighPerformanceModeSeed` (ON on LOW tier). Under it the app already drops the image
 *    cache, the preload, the video, the visualizer and the blur (PerformanceMode / LeanMode), so those are not seeded twice.
 *  · [canvas] — `applyCanvasDefaultOn` (cover and album canvas OFF on LOW tier).
 *  · [liquidGlass] — owner order 2026-09-17 (`LiquidGlassInteractiveHighTierV1AppliedKey`): glass master + interactive
 *    glass ON on HIGH tier when the hardware can draw it. `null` = this class has no opinion and the user's choice stays.
 */
data class PerformanceClassSeed(
    val highPerformanceMode: Boolean,
    val canvas: Boolean,
    val liquidGlass: Boolean?,
)

object PerformanceClasses {

    fun fromTier(tier: DeviceTier): PerformanceClass = when (tier) {
        DeviceTier.ULTRA, DeviceTier.LOW -> PerformanceClass.ENTRY
        DeviceTier.MID -> PerformanceClass.BALANCED
        DeviceTier.HIGH -> PerformanceClass.PREMIUM
    }

    fun effective(detected: PerformanceClass, choice: PerformanceClassChoice): PerformanceClass = when (choice) {
        PerformanceClassChoice.AUTO -> detected
        PerformanceClassChoice.ENTRY -> PerformanceClass.ENTRY
        PerformanceClassChoice.BALANCED -> PerformanceClass.BALANCED
        PerformanceClassChoice.PREMIUM -> PerformanceClass.PREMIUM
    }

    /** A missing or unknown stored value is [PerformanceClassChoice.AUTO]. */
    fun parseChoice(stored: String?): PerformanceClassChoice =
        PerformanceClassChoice.entries.firstOrNull { it.name == stored } ?: PerformanceClassChoice.AUTO

    /**
     * @param glassCapable the hardware can draw Liquid Glass at all (API 31+, not a TV or a car screen). The tier and
     *   the Performance mode are not part of it: the class is what decides those here, and `isGlassEligible` still
     *   gates the drawing at runtime.
     */
    fun seedFor(cls: PerformanceClass, glassCapable: Boolean): PerformanceClassSeed = when (cls) {
        PerformanceClass.ENTRY -> PerformanceClassSeed(highPerformanceMode = true, canvas = false, liquidGlass = false)
        PerformanceClass.BALANCED -> PerformanceClassSeed(highPerformanceMode = false, canvas = true, liquidGlass = null)
        PerformanceClass.PREMIUM -> PerformanceClassSeed(
            highPerformanceMode = false,
            canvas = true,
            liquidGlass = if (glassCapable) true else null,
        )
    }

    /** The settings keys (by name) a class writes, with their values. */
    fun seedValues(seed: PerformanceClassSeed): Map<String, Boolean> = buildMap {
        put(HighPerformanceModeKey.name, seed.highPerformanceMode)
        put(CanvasThumbnailAnimationKey.name, seed.canvas)
        put(AlbumCanvasEnabledKey.name, seed.canvas)
        seed.liquidGlass?.let {
            put(LiquidGlassGlobalEnabledKey.name, it)
            put(LiquidGlassInteractiveKey.name, it)
        }
    }

    /**
     * The automatic seed never overwrites: a key that already has a value — the user's choice, or an earlier one-time
     * seed that cannot be told apart from it — is left alone. Only keys that were never written are filled.
     */
    fun untouchedOnly(values: Map<String, Boolean>, present: Set<String>): Map<String, Boolean> =
        values.filterKeys { it !in present }

    /** Every key [seedValues] can name, so a writer can map a name back to its typed key. */
    val SEEDED_KEYS: List<Preferences.Key<Boolean>> = listOf(
        HighPerformanceModeKey,
        CanvasThumbnailAnimationKey,
        AlbumCanvasEnabledKey,
        LiquidGlassGlobalEnabledKey,
        LiquidGlassInteractiveKey,
    )

    /** Writes [values] into [p]. Keys outside [SEEDED_KEYS] are ignored. */
    fun write(p: MutablePreferences, values: Map<String, Boolean>) {
        for (key in SEEDED_KEYS) values[key.name]?.let { p[key] = it }
    }

    /** The keys of [SEEDED_KEYS] that hold a value in [p]. */
    fun presentIn(p: Preferences): Set<String> = SEEDED_KEYS.filter { p[it] != null }.map { it.name }.toSet()
}
