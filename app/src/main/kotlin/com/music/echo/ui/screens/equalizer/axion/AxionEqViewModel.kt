package iad1tya.echo.music.ui.screens.equalizer.axion

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import iad1tya.echo.music.eq.EqualizerService
import iad1tya.echo.music.eq.data.EQProfileRepository
import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.eq.data.EqMode
import iad1tya.echo.music.eq.data.FactoryPreset
import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.eq.data.ParametricEQBand
import iad1tya.echo.music.eq.data.SavedEQProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pure mapping: per-band dB gains + band types → ParametricEQ bands.
 * Gain is stored directly in dB (matches the desktop engine; no scaling).
 */
fun buildEqBands(gainsDb: FloatArray, types: IntArray, qs: FloatArray? = null): List<ParametricEQBand> =
    EqConstants.FREQUENCIES.mapIndexed { i, freq ->
        ParametricEQBand(
            frequency = freq,
            gain = gainsDb.getOrElse(i) { 0f }.toDouble(),
            // MANUAL GRAPHIC EQ (owner directive 2026-09-13): per-band Q when the user set one; the historic
            // fixed Q otherwise, so an untouched band sounds exactly as before.
            q = qs?.getOrNull(i)?.toDouble()?.coerceIn(PeqConstants.Q_MIN, PeqConstants.Q_MAX) ?: EqConstants.Q,
            filterType = graphicFilterType(i, types.getOrElse(i) { GRAPHIC_TYPE_AUTO }),
            enabled = true,
        )
    }

/** Graphic band filter-type codes. AUTO keeps the positional shelf/peak/shelf layout the EQ always had. */
const val GRAPHIC_TYPE_AUTO = 0
const val GRAPHIC_TYPE_PEAK = 1
const val GRAPHIC_TYPE_LOW_SHELF = 2
const val GRAPHIC_TYPE_HIGH_SHELF = 3

fun graphicFilterType(index: Int, code: Int): FilterType = when (code) {
    GRAPHIC_TYPE_PEAK -> FilterType.PK
    GRAPHIC_TYPE_LOW_SHELF -> FilterType.LSC
    GRAPHIC_TYPE_HIGH_SHELF -> FilterType.HSC
    else -> when (index) {
        0 -> FilterType.LSC
        EqConstants.BAND_COUNT - 1 -> FilterType.HSC
        else -> FilterType.PK
    }
}

/**
 * Parametric (PEQ) constraints. The 5–8 PEQ bands are fully user-defined (free frequency / Q / gain),
 * unlike the fixed 24 graphic centers. Same [ParametricEQBand] type → same DSP path.
 */
object PeqConstants {
    const val MIN_BANDS = 5
    // Owner directive 2026-09-13 ("lo más manual posible"): up to 16 free bands. The native engine
    // allocates 64 filter slots (SuperpoweredBridge NUM_EQ_FILTERS), shared with the Auto-EQ stage.
    const val MAX_BANDS = 16
    const val FREQ_MIN = 20.0
    const val FREQ_MAX = 20000.0
    const val Q_MIN = 0.3
    const val Q_MAX = 10.0
    const val GAIN_MIN = -18.0
    const val GAIN_MAX = 18.0
    const val Q_DEFAULT = 1.414

    /** Default 10-band PEQ — perfectly matches graphic EQ anchors. */
    val DEFAULT_FREQS = doubleArrayOf(31.5, 62.5, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)

    fun defaultBands(): List<ParametricEQBand> = DEFAULT_FREQS.mapIndexed { i, f ->
        ParametricEQBand(
            frequency = f,
            gain = 0.0,
            q = Q_DEFAULT,
            filterType = when (i) {
                0 -> FilterType.LSC
                EqConstants.BAND_COUNT - 1 -> FilterType.HSC
                else -> FilterType.PK
            },
            enabled = true
        )
    }
}

/**
 * Serializable DTO for persisting PEQ bands as JSON (the engine model [ParametricEQBand] is already
 * @Serializable, but we keep a stable, minimal {freqHz, q, gainDb, type} shape independent of it).
 */
@kotlinx.serialization.Serializable
data class PeqBandDto(
    val freqHz: Double,
    val q: Double,
    val gainDb: Double,
    val type: String, // FilterType.name (PK / LSC / HSC)
) {
    fun toBand(): ParametricEQBand = ParametricEQBand(
        frequency = freqHz,
        gain = gainDb,
        q = q,
        filterType = runCatching { FilterType.valueOf(type) }.getOrDefault(FilterType.PK),
        enabled = true,
    )

    companion object {
        fun from(b: ParametricEQBand): PeqBandDto =
            PeqBandDto(freqHz = b.frequency, q = b.q, gainDb = b.gain, type = b.filterType.name)
    }
}

/**
 * 10-band (EqConstants.BAND_COUNT) ISO octave graphic equalizer view model — desktop JR DSP Pro parity.
 * Band gains and pre-amp are kept in dB and pushed to the real biquad chain through
 * [EqualizerService] / [EQProfileRepository].
 */
@HiltViewModel
class AxionEqViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val equalizerService: EqualizerService,
    private val eqProfileRepository: EQProfileRepository,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("echo_eq_prefs", Context.MODE_PRIVATE)
    private val n = EqConstants.BAND_COUNT

    private val _enabled = MutableStateFlow(prefs.getBoolean("enabled", false))
    val enabled = _enabled.asStateFlow()

    // Auto-EQ now runs as its OWN cascaded correction stage that the manual EQ STACKS on top of (it no
    // longer replaces / locks the manual bands). This flag means "an Auto-EQ correction stage is present"
    // — NOT a lock on the manual editor.
    private val _autoEqActive = MutableStateFlow(prefs.getBoolean("autoeq_active", false))
    val autoEqActive = _autoEqActive.asStateFlow()

    // The Auto-EQ correction curve, kept independently from the manual graphic/parametric bands and
    // persisted as its own JSON (reuses the PeqBandDto serialization). Fed to the DSP as ParametricEQ.autoBands.
    private val _autoEqBands = MutableStateFlow(loadAutoEqBands())
    val autoEqBands = _autoEqBands.asStateFlow()

    private fun setAutoEqActive(active: Boolean) {
        _autoEqActive.value = active
        prefs.edit().putBoolean("autoeq_active", active).apply()
    }

    private fun loadAutoEqBands(): List<ParametricEQBand> {
        val json = prefs.getString("autoeq_bands", null) ?: return emptyList()
        return runCatching {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(PeqBandDto.serializer()),
                    json,
                )
                .map { it.toBand() }
        }.getOrNull() ?: emptyList()
    }

    private fun persistAutoEqBands() {
        val dtos = _autoEqBands.value.map { PeqBandDto.from(it) }
        val json = kotlinx.serialization.json.Json
            .encodeToString(kotlinx.serialization.builtins.ListSerializer(PeqBandDto.serializer()), dtos)
        prefs.edit().putString("autoeq_bands", json).apply()
    }

    /**
     * Re-enable the Auto-EQ correction stage from the persisted bands. No-op when no profile has
     * been stored yet (user must pick one from the Auto-EQ screen). Does NOT touch master [enabled].
     */
    fun reEnableAutoEq() {
        if (_autoEqBands.value.isEmpty()) return
        setAutoEqActive(true)
        if (_enabled.value) applyToService()
    }

    /** Remove the Auto-EQ correction stage entirely (manual EQ is untouched). Wired to "Quitar Auto-EQ". */
    fun clearAutoEq() {
        _autoEqBands.value = emptyList()
        persistAutoEqBands()
        setAutoEqActive(false)
        if (_enabled.value) applyToService()
    }

    // Band gains in dB (new "band24_" keys; legacy 10-band "band_" keys are intentionally ignored).
    private val _bandGains = MutableStateFlow(FloatArray(n) { prefs.getFloat("band24_$it", 0f) })
    val bandGains = _bandGains.asStateFlow()

    // Per-band filter type: 0=Peak, 1=LowShelf, 2=HighShelf.
    private val _bandTypes = MutableStateFlow(IntArray(n) { prefs.getInt("type24_$it", 0) })
    val bandTypes = _bandTypes.asStateFlow()

    // MANUAL GRAPHIC EQ (owner directive 2026-09-13: "todos los parámetros lo más manual posible").
    // New keys on purpose: the legacy type24_* array was never read by buildEqBands (every band was
    // positional), so honouring it now would silently turn the first/last shelves into peaks for anyone
    // with a stale value. gtype_* defaults to AUTO (= that same positional layout) and gq_* to the
    // historic Q, so an untouched EQ is bit-identical to before.
    private val _graphicTypes = MutableStateFlow(IntArray(n) { prefs.getInt("gtype_$it", GRAPHIC_TYPE_AUTO) })
    val graphicTypes = _graphicTypes.asStateFlow()
    private val _graphicQs = MutableStateFlow(FloatArray(n) { prefs.getFloat("gq_$it", EqConstants.Q.toFloat()) })
    val graphicQs = _graphicQs.asStateFlow()

    /** Live Q edit for a graphic band (persisted on commit, like gains). */
    fun setGraphicBandQLive(index: Int, q: Float) {
        if (index !in 0 until n) return
        val arr = _graphicQs.value.copyOf()
        arr[index] = q.coerceIn(PeqConstants.Q_MIN.toFloat(), PeqConstants.Q_MAX.toFloat())
        _graphicQs.value = arr
        _isDirty.value = true
        if (_enabled.value) equalizerService.applyProfile(liveProfile())
    }

    /** Filter type for a graphic band: AUTO / PEAK / LOW_SHELF / HIGH_SHELF. Persists + applies. */
    fun setGraphicBandType(index: Int, code: Int) {
        if (index !in 0 until n) return
        val arr = _graphicTypes.value.copyOf()
        arr[index] = code.coerceIn(GRAPHIC_TYPE_AUTO, GRAPHIC_TYPE_HIGH_SHELF)
        _graphicTypes.value = arr
        prefs.edit().putInt("gtype_$index", arr[index]).apply()
        _isDirty.value = true
        // Apply at once like gain and Q do; commit() only re-applies later through the repository collector.
        if (_enabled.value) equalizerService.applyProfile(liveProfile())
        commit()
    }

    // Fallback 2.3f = the current default (HALLAZGO-046 shipped +3; the owner lowered it to +2 on
    // 2026-08-26, then raised it to +2.3 on 2026-09-05 with the Aura Hi-Res v2 directive).
    // Migrations write the value explicitly; this only matters before they run.
    private val _preamp = MutableStateFlow(prefs.getFloat("preampDb", 2.2f))
    val preamp = _preamp.asStateFlow()

    // EQ editing mode: GRAPHIC (10-band, EqConstants.BAND_COUNT, default) vs PARAMETRIC (5–8 free PEQ bands). Both curves are
    // kept independently (separate state + prefs keys) so switching modes never loses the other.
    private val _eqMode = MutableStateFlow(
        runCatching { EqMode.valueOf(prefs.getString("eq_mode", "GRAPHIC")!!) }.getOrDefault(EqMode.GRAPHIC)
    )
    val eqMode = _eqMode.asStateFlow()

    // Parametric (PEQ) bands — defaults to the 6 anchor bands. Persisted as a JSON DTO list.
    private val _peqBands = MutableStateFlow(loadPeqBands())
    val peqBands = _peqBands.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty = _isDirty.asStateFlow()

    val customProfiles = eqProfileRepository.profiles.map { profiles ->
        profiles.filter { it.isCustom && it.id != "echo_tuning" }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Keeps [enabled] honest when the flag is written from OUTSIDE this instance.
     *
     * `echo_eq_prefs["enabled"]` has three writers: this view model, [MusicService.applyEqForCurrentOutput]
     * (per-output profiles, MusicService.kt:1385) and the seeding migration (App.kt:900). `_enabled` was a
     * one-shot read taken at construction, so any of those made this flow stale — and every read site that
     * asks the view model instead of the file (the EQ screen switch, and now the new player's engine bar)
     * would show a lie until the screen was rebuilt.
     *
     * Read-only sync: it assigns the flow and NOTHING else. It never calls [applyToService] / [setEnabled],
     * so it cannot write a preference, re-emit a profile or touch the DSP — reacting to an external write
     * must not become a second write. Held in a field because SharedPreferences keeps only weak references
     * to its listeners.
     */
    private val enabledSync = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == null || key == "enabled") _enabled.value = p.getBoolean("enabled", false)
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(enabledSync)
        // Migrated users from before the stacking change have autoeq_active=true but no autoeq_bands JSON
        // (their old Auto-EQ curve lives in the manual band24_* gains). Reconcile so they don't see a phantom
        // "Auto-EQ activo" chip for a correction stage that doesn't exist.
        if (_autoEqActive.value && _autoEqBands.value.isEmpty()) setAutoEqActive(false)
        // Do NOT re-apply the EQ here. applyToService() persists echo_tuning as the active profile, so the
        // service already applies it at startup (MusicService collects eqProfileRepository.activeProfile).
        // Re-applying when the EQ screen opens just re-emits the same profile and caused an audible blip
        // ("the sound changes as if the EQ activates" on entry). The chain is already live from startup; user
        // edits re-apply through their own mutators.
    }

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        prefs.edit().putBoolean("enabled", enabled).apply()
        if (enabled) {
            applyToService()
        } else {
            // Clear BOTH profiles. Leaving a stale unsavedProfile would make the MusicService combine
            // resolve unsaved ?: null = stale and re-apply it, so the EQ would not actually turn off.
            viewModelScope.launch {
                eqProfileRepository.setUnsavedProfile(null)
                eqProfileRepository.setActiveProfile(null)
            }
            equalizerService.disable()
        }
    }

    fun setBandGain(index: Int, gainDb: Float) {
        if (index !in 0 until n) return
        val v = gainDb.coerceIn(EqConstants.GAIN_MIN, EqConstants.GAIN_MAX)
        val arr = _bandGains.value.copyOf()
        arr[index] = v
        _bandGains.value = arr
        prefs.edit().putFloat("band24_$index", v).apply()
        _isDirty.value = true
        if (_enabled.value) applyToService()
    }

    fun setBandsGains(gains: FloatArray, fromUser: Boolean = false) {
        val arr = FloatArray(n) { i ->
            gains.getOrElse(i) { 0f }.coerceIn(EqConstants.GAIN_MIN, EqConstants.GAIN_MAX)
        }
        _bandGains.value = arr
        val editor = prefs.edit()
        arr.forEachIndexed { i, f -> editor.putFloat("band24_$i", f) }
        editor.apply()
        _isDirty.value = fromUser
        if (_enabled.value) applyToService()
    }

    fun setPreamp(db: Float) {
        val v = db.coerceIn(EqConstants.PREAMP_MIN, EqConstants.PREAMP_MAX)
        _preamp.value = v
        prefs.edit().putFloat("preampDb", v).apply()
        _isDirty.value = true
        if (_enabled.value) applyToService()
    }

    /** Switch between GRAPHIC and PARAMETRIC. The inactive curve is preserved (separate state/prefs). */
    fun setEqMode(m: EqMode) {
        _eqMode.value = m
        prefs.edit().putString("eq_mode", m.name).apply()
        if (_enabled.value) applyToService()
    }

    // ---- Parametric (PEQ) editing ----------------------------------------------------------------

    private fun loadPeqBands(): List<ParametricEQBand> {
        val json = prefs.getString("peq_bands", null) ?: return PeqConstants.defaultBands()
        return runCatching {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(PeqBandDto.serializer()),
                    json,
                )
                .map { it.toBand() }
                .takeIf { it.size in PeqConstants.MIN_BANDS..PeqConstants.MAX_BANDS }
        }.getOrNull() ?: PeqConstants.defaultBands()
    }

    private fun persistPeqBands() {
        val dtos = _peqBands.value.map { PeqBandDto.from(it) }
        val json = kotlinx.serialization.json.Json
            .encodeToString(kotlinx.serialization.builtins.ListSerializer(PeqBandDto.serializer()), dtos)
        prefs.edit().putString("peq_bands", json).apply()
    }

    /**
     * Edit a single PEQ band LIVE (any null arg is left unchanged). Clamps to PEQ ranges. Mirrors the
     * graphic [setBandGainLive] split: this only updates the DSP coefficients in place (no disk writes,
     * no profile save) so typing doesn't hit disk every keystroke. Persist via [commitPeq].
     */
    fun setPeqBand(index: Int, freq: Double? = null, q: Double? = null, gain: Double? = null, type: FilterType? = null) {
        val list = _peqBands.value
        if (index !in list.indices) return
        val cur = list[index]
        val updated = cur.copy(
            frequency = (freq ?: cur.frequency).coerceIn(PeqConstants.FREQ_MIN, PeqConstants.FREQ_MAX),
            q = (q ?: cur.q).coerceIn(PeqConstants.Q_MIN, PeqConstants.Q_MAX),
            gain = (gain ?: cur.gain).coerceIn(PeqConstants.GAIN_MIN, PeqConstants.GAIN_MAX),
            filterType = type ?: cur.filterType,
        )
        _peqBands.value = list.toMutableList().also { it[index] = updated }
        _isDirty.value = true
        if (_enabled.value) equalizerService.applyProfile(liveProfile())
    }

    /** Append a sensible default PEQ band (max 8). Inserts at 1 kHz, flat, peak. */
    fun addPeqBand() {
        val list = _peqBands.value
        if (list.size >= PeqConstants.MAX_BANDS) return
        val band = ParametricEQBand(
            frequency = 1000.0, gain = 0.0, q = PeqConstants.Q_DEFAULT, filterType = FilterType.PK, enabled = true,
        )
        _peqBands.value = list + band
        _isDirty.value = true
        commitPeq()
        if (_enabled.value) applyToService()
    }

    /** Remove a PEQ band (min 5). */
    fun removePeqBand(index: Int) {
        val list = _peqBands.value
        if (list.size <= PeqConstants.MIN_BANDS || index !in list.indices) return
        _peqBands.value = list.toMutableList().also { it.removeAt(index) }
        _isDirty.value = true
        commitPeq()
        if (_enabled.value) applyToService()
    }

    /** Reset the PEQ back to the default flat 6-band anchors (clears any custom curve). Persists + applies. */
    fun resetPeq() {
        _peqBands.value = PeqConstants.defaultBands()
        _isDirty.value = true
        commitPeq()
        if (_enabled.value) applyToService()
    }

    /**
     * SAFETY NET: whatever the user left on screen is persisted when the EQ screen goes away, even if
     * they never saved a preset and never triggered a settle.
     *
     * Almost every edit already persists on its own (slider release -> commit, add/remove/reset PEQ ->
     * commitPeq, band type / preamp -> immediate prefs write). The gap was the live edits that persist
     * only on "value settle": drag a PEQ frequency/Q field and leave the screen without the settle
     * firing, and the tuning the user was listening to was gone next launch. The owner's rule is that the
     * EQ always comes back exactly as he left it, saved preset or not.
     *
     * Device-assigned profiles are deliberately NOT affected: applyEqForCurrentOutput re-applies the
     * profile bound to the current output and overwrites the unsaved one, which is the intended
     * precedence — an output with its own tuning keeps it.
     */
    override fun onCleared() {
        super.onCleared()
        runCatching { prefs.unregisterOnSharedPreferenceChangeListener(enabledSync) }
        if (_isDirty.value) {
            runCatching { commit() }
        }
    }

    /** Persist the PEQ bands JSON — call on text-field focus loss / value settle (or via [commit]). */
    fun commitPeq() {
        persistPeqBands()
        if (_enabled.value) viewModelScope.launch {
            val p = liveProfile()
            eqProfileRepository.saveProfile(p)
            // Keep unsavedProfile in sync with the committed edit. MusicService observes
            // combine(activeProfile, unsavedProfile){ unsaved ?: active }; setActiveProfile below fires it,
            // and a STALE unsaved (live drag bypasses setUnsavedProfile) would otherwise win and revert the
            // edit. Syncing it means the combine re-applies the SAME coefficients (sonic no-op).
            eqProfileRepository.setUnsavedProfile(p)
            eqProfileRepository.setActiveProfile(p.id)
        }
    }

    /**
     * Apply a full profile (bands + preamp + enable) in ONE shot. Calling setBandsGains + setPreamp +
     * setEnabled separately fired applyToService() three times (each doing 2 DB writes + a DSP
     * re-apply), which stuttered the audio every time an AutoEq profile was selected. This batches it
     * into a single apply.
     */
    fun applyProfileBatch(gains: FloatArray, preampDb: Float, isAutoEq: Boolean = false) {
        val arr = FloatArray(n) { i ->
            gains.getOrElse(i) { 0f }.coerceIn(EqConstants.GAIN_MIN, EqConstants.GAIN_MAX)
        }
        _preamp.value = preampDb.coerceIn(EqConstants.PREAMP_MIN, EqConstants.PREAMP_MAX)
        _enabled.value = true
        if (isAutoEq) {
            // Auto-EQ correction stage: store the projected curve as its OWN autoBands (24 PK bands at the
            // ISO centers). The manual graphic/parametric EQ is left UNTOUCHED so it stacks on top.
            _autoEqBands.value = buildEqBands(arr, IntArray(n) { 0 })
            persistAutoEqBands()
            prefs.edit().apply {
                putFloat("preampDb", _preamp.value)
                putBoolean("enabled", true)
            }.apply()
            setAutoEqActive(true)
        } else {
            // Loading a saved graphic profile: write the manual 10-band (EqConstants.BAND_COUNT) curve as before.
            _bandGains.value = arr
            prefs.edit().apply {
                arr.forEachIndexed { i, f -> putFloat("band24_$i", f) }
                putFloat("preampDb", _preamp.value)
                putBoolean("enabled", true)
            }.apply()
        }
        _isDirty.value = true
        applyToService()
    }

    fun setBandType(index: Int, type: Int) {
        if (index !in 0 until n) return
        val arr = _bandTypes.value.copyOf()
        arr[index] = type.coerceIn(0, 2)
        _bandTypes.value = arr
        prefs.edit().putInt("type24_$index", arr[index]).apply()
        _isDirty.value = true
        if (_enabled.value) applyToService()
    }

    fun applyPreset(preset: FactoryPreset) {
        setBandsGains(preset.gains.copyOf(), fromUser = true)
    }

    fun reset() {
        _preamp.value = 0f
        // A full reset also returns every graphic band to its default Q and positional filter type.
        _graphicQs.value = FloatArray(n) { EqConstants.Q.toFloat() }
        _graphicTypes.value = IntArray(n) { GRAPHIC_TYPE_AUTO }
        prefs.edit()
            .putFloat("preampDb", 0f)
            .apply {
                for (i in 0 until n) {
                    putFloat("gq_$i", EqConstants.Q.toFloat())
                    putInt("gtype_$i", GRAPHIC_TYPE_AUTO)
                }
            }
            .apply()
        setBandsGains(FloatArray(n) { 0f })
    }

    fun saveCustomProfile(name: String) {
        viewModelScope.launch {
            val profile = SavedEQProfile(
                id = "custom_${System.currentTimeMillis()}",
                name = name,
                deviceModel = "Equalizer",
                bands = allBands(),
                preamp = _preamp.value.toDouble(),
                isCustom = true,
                isActive = true,
                // Save the active effects + their levels alongside the EQ curve.
                effects = iad1tya.echo.music.eq.data.SoundEffectsSnapshot.capture(context),
            )
            eqProfileRepository.saveProfile(profile)
            eqProfileRepository.setActiveProfile(profile.id)
            _isDirty.value = false
        }
    }

    /**
     * Apply a saved profile: restore its EQ bands + preamp AND its sound-effects snapshot.
     *
     * Mode is detected by band count: exactly [EqConstants.BAND_COUNT] (24) bands → graphic profile;
     * anything else (5–8 free bands) → a parametric profile. Either branch ends in the matching mode +
     * an apply so the curve is audible, and loading the other kind switches the mode accordingly.
     */
    fun applySavedProfile(profile: SavedEQProfile) {
        if (profile.bands.size != EqConstants.BAND_COUNT) {
            // Parametric profile: restore the 5–8 free bands, switch to PARAMETRIC, apply. The Auto-EQ
            // correction stage (if any) is left in place — the loaded manual curve stacks on top of it.
            val bands = profile.bands
                .map { it.copy(enabled = true) }
                .let { list ->
                    when {
                        list.size < PeqConstants.MIN_BANDS ->
                            list + PeqConstants.defaultBands().drop(list.size)
                        list.size > PeqConstants.MAX_BANDS -> list.take(PeqConstants.MAX_BANDS)
                        else -> list
                    }
                }
            _peqBands.value = bands
            _preamp.value = profile.preamp.toFloat().coerceIn(EqConstants.PREAMP_MIN, EqConstants.PREAMP_MAX)
            _enabled.value = true
            _eqMode.value = EqMode.PARAMETRIC
            prefs.edit()
                .putFloat("preampDb", _preamp.value)
                .putBoolean("enabled", true)
                .putString("eq_mode", EqMode.PARAMETRIC.name)
                .apply()
            persistPeqBands()
            _isDirty.value = true
            applyToService()
        } else {
            // Graphic profile: positional 10-band (EqConstants.BAND_COUNT) load + ensure GRAPHIC mode (switches back from PEQ).
            _eqMode.value = EqMode.GRAPHIC
            prefs.edit().putString("eq_mode", EqMode.GRAPHIC.name).apply()
            val gains = FloatArray(n) { i -> profile.bands.getOrNull(i)?.gain?.toFloat() ?: 0f }
            applyProfileBatch(gains, profile.preamp.toFloat())
        }
        viewModelScope.launch {
            eqProfileRepository.setActiveProfile(profile.id)
            iad1tya.echo.music.eq.data.SoundEffectsSnapshot.apply(context, profile.effects)
        }
    }

    /** Write all custom profiles (EQ + effects) as JSON to a file the user picked (export). */
    fun exportProfiles(uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val list = eqProfileRepository.getAllProfiles().filter { it.isCustom }
                val text = kotlinx.serialization.json.Json { prettyPrint = true }
                    .encodeToString(kotlinx.serialization.builtins.ListSerializer(SavedEQProfile.serializer()), list)
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }
        }
    }

    /** Import profiles from a previously-exported JSON file. */
    fun importProfiles(uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: return@launch
                val list = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .decodeFromString(kotlinx.serialization.builtins.ListSerializer(SavedEQProfile.serializer()), text)
                list.forEach { p ->
                    eqProfileRepository.saveProfile(p.copy(id = "custom_${System.currentTimeMillis()}_${p.name.hashCode()}", isActive = false, isCustom = true))
                }
            }
        }
    }

    fun deleteProfiles(ids: List<String>) {
        viewModelScope.launch { ids.forEach { eqProfileRepository.deleteProfile(it) } }
    }

    private fun applyToService() {
        viewModelScope.launch {
            val profile = SavedEQProfile(
                id = "echo_tuning",
                name = "JR Tuning",
                deviceModel = "Equalizer",
                bands = allBands(),
                // Auto-EQ runs as a separate cascaded correction stage the manual EQ stacks on top of.
                autoBands = if (_autoEqActive.value) _autoEqBands.value else emptyList(),
                preamp = _preamp.value.toDouble(),
                isCustom = false,
                isActive = true,
            )
            eqProfileRepository.setUnsavedProfile(profile)
            equalizerService.applyProfile(profile)
        }
    }

    /** Current UI state as a live EQ profile (not yet persisted). */
    private fun liveProfile(): SavedEQProfile = SavedEQProfile(
        id = "echo_tuning",
        name = "JR Tuning",
        deviceModel = "Equalizer",
        bands = allBands(),
        // Auto-EQ runs as a separate cascaded correction stage the manual EQ stacks on top of.
        autoBands = if (_autoEqActive.value) _autoEqBands.value else emptyList(),
        preamp = _preamp.value.toDouble(),
        isCustom = false,
        isActive = true,
    )

    /**
     * Live band drag: update the DSP coefficients in place (no disk writes, no profile save, no
     * player re-seek) so the change is heard in real time without stutter. Persist via [commit].
     */
    fun setBandGainLive(index: Int, gainDb: Float) {
        if (index !in 0 until n) return
        val v = gainDb.coerceIn(EqConstants.GAIN_MIN, EqConstants.GAIN_MAX)
        val arr = _bandGains.value.copyOf()
        arr[index] = v
        _bandGains.value = arr
        _isDirty.value = true
        if (_enabled.value) equalizerService.applyProfile(liveProfile())
    }

    // ── In-engine verification (owner directive 2026-09-13: "funcionando a la perfección") ─────────────
    private val _verification = MutableStateFlow<EqVerification?>(null)
    val verification = _verification.asStateFlow()

    /**
     * Measures the current curve inside the real engine: a sine at each test frequency through the same
     * coefficient code the live chain uses, compared with the drawn curve (EqResponse), plus the pink-noise
     * headroom test at -0.1 dBFS. Runs off the main thread; touches no live audio state.
     */
    fun runVerification() {
        _verification.value = EqVerification.running()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val bands = ((if (_autoEqActive.value) _autoEqBands.value else emptyList()) + allBands()).filter { it.enabled }
            val freqs = EqVerification.TEST_FREQUENCIES
            val measured = equalizerService.measure(bands, _preamp.value, freqs, EqVerification.SAMPLE_RATE)
            _verification.value = if (measured == null) {
                EqVerification.engineUnavailable()
            } else {
                val (response, headroom) = measured
                EqVerification(
                    running = false,
                    engineReady = true,
                    rows = freqs.mapIndexed { i, f ->
                        EqVerificationRow(
                            frequency = f,
                            expectedDb = iad1tya.echo.music.eq.EqResponse.combinedMagnitudeDb(bands, f, EqVerification.SAMPLE_RATE.toDouble()),
                            measuredDb = response.getOrElse(i) { -120f }.toDouble(),
                        )
                    },
                    peakInDb = headroom.getOrElse(0) { 0f },
                    peakAfterEqDb = headroom.getOrElse(1) { 0f },
                    peakOutDb = headroom.getOrElse(2) { 0f },
                )
            }
        }
    }

    fun dismissVerification() {
        _verification.value = null
    }

    /**
     * A/B listening: while [bypass] is true the DSP runs without the EQ (bands and preamp); releasing
     * re-applies the current tuning. Nothing is persisted and the EQ switch is untouched.
     */
    fun previewBypass(bypass: Boolean) {
        if (!_enabled.value) return
        if (bypass) equalizerService.disable() else equalizerService.applyProfile(liveProfile())
    }

    /** Live preamp drag (see [setBandGainLive]). */
    fun setPreampLive(db: Float) {
        val v = db.coerceIn(EqConstants.PREAMP_MIN, EqConstants.PREAMP_MAX)
        _preamp.value = v
        _isDirty.value = true
        if (_enabled.value) equalizerService.applyProfile(liveProfile())
    }

    /**
     * The full filter set sent to the DSP. Branches on the active mode — both produce
     * [ParametricEQBand]s consumed by the SAME engine path (CustomEqualizerAudioProcessor.createFilters).
     */
    private fun allBands(): List<ParametricEQBand> = when (_eqMode.value) {
        EqMode.GRAPHIC -> buildEqBands(_bandGains.value, _graphicTypes.value, _graphicQs.value)
        EqMode.PARAMETRIC -> _peqBands.value.filter { it.enabled }
    }

    /** Persist the current tuning once — call on slider release (onValueChangeFinished). */
    fun commit() {
        val editor = prefs.edit()
        _bandGains.value.forEachIndexed { i, f -> editor.putFloat("band24_$i", f) }
        _bandTypes.value.forEachIndexed { i, t -> editor.putInt("type24_$i", t) }
        _graphicTypes.value.forEachIndexed { i, t -> editor.putInt("gtype_$i", t) }
        _graphicQs.value.forEachIndexed { i, q -> editor.putFloat("gq_$i", q) }
        editor.putFloat("preampDb", _preamp.value)
        editor.apply()
        // PEQ curve is persisted independently of the graphic bands so neither overwrites the other.
        persistPeqBands()
        if (_enabled.value) viewModelScope.launch {
            val p = liveProfile()
            eqProfileRepository.saveProfile(p)
            // Keep unsavedProfile in sync with the committed edit. MusicService observes
            // combine(activeProfile, unsavedProfile){ unsaved ?: active }; setActiveProfile below fires it,
            // and a STALE unsaved (live drag bypasses setUnsavedProfile) would otherwise win and revert the
            // edit. Syncing it means the combine re-applies the SAME coefficients (sonic no-op).
            eqProfileRepository.setUnsavedProfile(p)
            eqProfileRepository.setActiveProfile(p.id)
        }
    }
}


data class EqVerificationRow(val frequency: Double, val expectedDb: Double, val measuredDb: Double) {
    val differenceDb: Double get() = measuredDb - expectedDb
    val passes: Boolean get() = kotlin.math.abs(differenceDb) <= EqVerification.TOLERANCE_DB
}

data class EqVerification(
    val running: Boolean,
    val engineReady: Boolean,
    val rows: List<EqVerificationRow>,
    val peakInDb: Float,
    val peakAfterEqDb: Float,
    val peakOutDb: Float,
) {
    val responsePasses: Boolean get() = rows.isNotEmpty() && rows.all { it.passes }
    /** The output never reaches full scale: limited to the -0.3 dBFS ceiling (small tolerance). */
    val headroomPasses: Boolean get() = engineReady && peakOutDb <= HEADROOM_LIMIT_DBFS

    companion object {
        const val SAMPLE_RATE = 48_000
        const val TOLERANCE_DB = 0.5
        const val HEADROOM_LIMIT_DBFS = -0.2f
        val TEST_FREQUENCIES = doubleArrayOf(
            31.5, 45.0, 62.5, 90.0, 125.0, 180.0, 250.0, 355.0, 500.0, 710.0,
            1000.0, 1400.0, 2000.0, 2800.0, 4000.0, 5600.0, 8000.0, 11200.0, 16000.0,
        )

        fun running() = EqVerification(true, true, emptyList(), 0f, 0f, 0f)
        fun engineUnavailable() = EqVerification(false, false, emptyList(), 0f, 0f, 0f)
    }
}
