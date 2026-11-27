package iad1tya.echo.music.eq.autoeq

import android.content.Context
import android.content.SharedPreferences
import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.eq.data.EqDeviceProfileStore
import iad1tya.echo.music.eq.data.ParametricEQBand
import iad1tya.echo.music.ui.screens.equalizer.axion.PeqBandDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Registry row 915: puts the automatic AutoEq correction of the current output into the Auto-EQ stage, or takes it
 * out, writing `echo_eq_prefs` with [AutoEqDeviceSwitch]'s plan. Shared by MusicService (every output change) and the
 * Auto-EQ screens (accepting the offer, removing it, changing the boost cap); each caller then plays the [Stage] the
 * way it already plays the EQ. Nothing is logged here: the device key holds the Bluetooth name.
 */
object AutoEqDeviceCorrection {

    /** The Auto-EQ stage after a change: the bands to play ([autoBands] empty = stage off) and the master preamp. */
    data class Stage(val active: Boolean, val autoBands: List<ParametricEQBand>, val preampDb: Float)

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PeqBandDto.serializer())

    fun eqPrefs(context: Context): SharedPreferences = context.getSharedPreferences("echo_eq_prefs", Context.MODE_PRIVATE)

    fun encodeBands(bands: List<ParametricEQBand>): String = json.encodeToString(serializer, bands.map { PeqBandDto.from(it) })

    fun decodeBands(text: String?): List<ParametricEQBand> =
        text?.let { runCatching { json.decodeFromString(serializer, it).map { d -> d.toBand() } }.getOrNull() } ?: emptyList()

    fun readState(p: SharedPreferences): AutoEqDeviceSwitch.State = AutoEqDeviceSwitch.State(
        active = p.getBoolean(AutoEqDeviceSwitch.KEY_ACTIVE, false),
        bandsJson = p.getString(AutoEqDeviceSwitch.KEY_BANDS, null),
        autoPreamp = p.takeIf { it.contains(AutoEqDeviceSwitch.KEY_PREAMP) }?.getFloat(AutoEqDeviceSwitch.KEY_PREAMP, 0f),
        preampBefore = p.takeIf { it.contains(AutoEqDeviceSwitch.KEY_PREAMP_BEFORE) }
            ?.getFloat(AutoEqDeviceSwitch.KEY_PREAMP_BEFORE, 0f),
        masterPreamp = p.getFloat(AutoEqDeviceSwitch.KEY_MASTER_PREAMP, EqConstants.DEFAULT_PREAMP_DB),
        owner = p.getString(AutoEqDeviceSwitch.KEY_OWNER, null),
        stashBandsJson = p.getString(AutoEqDeviceSwitch.KEY_STASH_BANDS, null),
        stashActive = p.takeIf { it.contains(AutoEqDeviceSwitch.KEY_STASH_ACTIVE) }
            ?.getBoolean(AutoEqDeviceSwitch.KEY_STASH_ACTIVE, false),
        stashPreamp = p.takeIf { it.contains(AutoEqDeviceSwitch.KEY_STASH_PREAMP) }
            ?.getFloat(AutoEqDeviceSwitch.KEY_STASH_PREAMP, 0f),
    )

    /** The safe correction assigned to [outputKey] ([AutoEqSafety]), with the preamp for this cascade, or null. */
    fun correctionFor(
        context: Context,
        outputKey: String,
        manualBands: List<ParametricEQBand>,
        autoHeadroomOn: Boolean,
    ): AutoEqDeviceSwitch.DeviceCorrection? {
        val line = EqDeviceProfileStore.assignedAutoEqLine(context, outputKey) ?: return null
        val entry = AutoEqOfflineCatalog.parseLine(line) ?: return null
        val bands = AutoEqSafety.safeBands(entry.toProfile(), EqDeviceProfileStore.autoEqMaxBoostDb(context).toDouble())
        if (bands.isEmpty()) return null
        val preamp = AutoEqSafety.devicePreampDb(bands + manualBands, autoHeadroomOn).toFloat()
            .coerceIn(EqConstants.PREAMP_MIN, EqConstants.PREAMP_MAX)
        return AutoEqDeviceSwitch.DeviceCorrection(encodeBands(bands), preamp)
    }

    /**
     * Applies the plan for [outputKey] to `echo_eq_prefs` (one write) and returns the resulting [Stage], or null when
     * nothing changed; [releaseOnly] = [AutoEqDeviceSwitch.planRelease]. Blocking prefs reads: call off the main thread.
     */
    fun sync(
        context: Context,
        outputKey: String,
        manualBands: List<ParametricEQBand>,
        autoHeadroomOn: Boolean,
        force: Boolean = false,
        releaseOnly: Boolean = false,
    ): Stage? {
        val p = eqPrefs(context)
        val state = readState(p)
        val edits = if (releaseOnly) {
            AutoEqDeviceSwitch.planRelease(state, outputKey)
        } else {
            AutoEqDeviceSwitch.plan(state, outputKey, correctionFor(context, outputKey, manualBands, autoHeadroomOn), force)
        }
        if (edits.isEmpty()) return null
        p.edit().apply {
            edits.forEach { (key, value) ->
                when (value) {
                    null -> remove(key)
                    is Boolean -> putBoolean(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                }
            }
        }.apply()
        val active = p.getBoolean(AutoEqDeviceSwitch.KEY_ACTIVE, false)
        return Stage(
            active = active,
            autoBands = if (active) decodeBands(p.getString(AutoEqDeviceSwitch.KEY_BANDS, null)).take(48) else emptyList(),
            preampDb = p.getFloat(AutoEqDeviceSwitch.KEY_MASTER_PREAMP, EqConstants.DEFAULT_PREAMP_DB)
                .coerceIn(EqConstants.PREAMP_MIN, EqConstants.PREAMP_MAX),
        )
    }

    /**
     * The stage as it stands when it is [outputKey]'s correction and on, else null. Used after a device's saved profile
     * was played (it carries its own Auto-EQ bands and preamp): the master preamp goes back to the correction's.
     */
    fun ownedStage(context: Context, outputKey: String): Stage? {
        val p = eqPrefs(context)
        val state = readState(p)
        if (state.owner != outputKey || !state.active) return null
        val preamp = (state.autoPreamp ?: state.masterPreamp).coerceIn(EqConstants.PREAMP_MIN, EqConstants.PREAMP_MAX)
        p.edit().putFloat(AutoEqDeviceSwitch.KEY_MASTER_PREAMP, preamp).apply()
        return Stage(true, decodeBands(state.bandsJson).take(48), preamp)
    }

    /**
     * A manual Auto-EQ choice: what a device's correction kept aside is dropped. A profile picked from the list
     * ([keepOwner] false) makes the stage the user's again, so leaving the headphone does not switch it off, and over a
     * device's correction it leaves that device's `user:` mark ([AutoEqDeviceSwitch.ownerAfterManualPick]), so the
     * correction does not come back over the pick while that headphone stays connected. «Quitar Auto-EQ» ([keepOwner]
     * true) keeps the owner mark, so the removal holds while that headphone stays connected and the correction only
     * comes back the next time it connects.
     */
    fun forgetDeviceOwnership(p: SharedPreferences, keepOwner: Boolean = false) {
        val owner = if (keepOwner) null else AutoEqDeviceSwitch.ownerAfterManualPick(p.getString(AutoEqDeviceSwitch.KEY_OWNER, null))
        p.edit().apply {
            AutoEqDeviceSwitch.DEVICE_KEYS.forEach { if (it != AutoEqDeviceSwitch.KEY_OWNER) remove(it) }
            if (!keepOwner) {
                if (owner == null) remove(AutoEqDeviceSwitch.KEY_OWNER) else putString(AutoEqDeviceSwitch.KEY_OWNER, owner)
            }
        }.apply()
    }
}
