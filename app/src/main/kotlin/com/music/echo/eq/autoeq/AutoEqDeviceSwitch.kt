package iad1tya.echo.music.eq.autoeq

/**
 * Pure decision for the per-device automatic correction (registry row 915): what to write in `echo_eq_prefs` when the
 * audio output changes. The Auto-EQ stage keys are the ones the Auto-EQ screen already owns (rows 792, 802); this only
 * adds who put the stage there ([KEY_OWNER]) and a copy of what the user had before ([KEY_STASH_BANDS] …).
 *
 *  - The output has a correction and the stage is not already this device's → the correction goes in, marked as the
 *    device's. If the stage held the user's own pick (no owner), it is kept aside first, and the user's preamp is saved
 *    in `autoeq_preamp_before` exactly as switching Auto-EQ on by hand does (row 802).
 *  - The stage is this device's already → nothing (an unrelated device joining must not undo a switch-off the user made
 *    while listening). [plan] with `force` re-writes it — only while it is on — when its settings change; `force` never
 *    puts a correction back on a stage the device does not hold.
 *  - The user picked a profile by hand over this headphone's correction ([ownerAfterManualPick]: owner
 *    `user:<outputKey>`) → nothing while that headphone stays connected; any other output only drops the mark, so the
 *    pick stays and the correction is offered to the stage again the next time the headphone connects.
 *  - The output has no correction and the stage is a device's → the device's correction goes away and what the user had
 *    comes back: their own Auto-EQ pick (on or off, with its preamp), or none and their preamp.
 *  - The stage is the user's own pick (no owner) → never touched.
 *
 * The result is a list of edits: key → Boolean / Float / String, or null to remove the key. Empty = nothing to do.
 */
object AutoEqDeviceSwitch {
    const val KEY_ACTIVE = "autoeq_active"
    const val KEY_BANDS = "autoeq_bands"
    const val KEY_PREAMP = "autoeq_preamp"
    const val KEY_PREAMP_BEFORE = "autoeq_preamp_before"
    const val KEY_MASTER_PREAMP = "preampDb"
    const val KEY_OWNER = "autoeq_device_owner"
    const val KEY_STASH_BANDS = "autoeq_device_stash_bands"
    const val KEY_STASH_ACTIVE = "autoeq_device_stash_active"
    const val KEY_STASH_PREAMP = "autoeq_device_stash_preamp"

    /** The keys a manual Auto-EQ choice clears: from then on the stage is the user's again. */
    val DEVICE_KEYS = listOf(KEY_OWNER, KEY_STASH_BANDS, KEY_STASH_ACTIVE, KEY_STASH_PREAMP)

    /** [KEY_OWNER] after a profile picked by hand over a device's correction: `user:<outputKey>`. */
    const val USER_PICK_PREFIX = "user:"

    /**
     * [KEY_OWNER] after a profile is picked by hand from the list: over a device's correction it becomes that device's
     * `user:` mark (the pick holds while the headphone stays connected); otherwise there is no owner.
     */
    fun ownerAfterManualPick(owner: String?): String? = when {
        owner == null -> null
        owner.startsWith(USER_PICK_PREFIX) -> owner
        else -> USER_PICK_PREFIX + owner
    }

    data class State(
        val active: Boolean,
        val bandsJson: String?,
        val autoPreamp: Float?,
        val preampBefore: Float?,
        val masterPreamp: Float,
        val owner: String?,
        val stashBandsJson: String?,
        val stashActive: Boolean?,
        val stashPreamp: Float?,
    )

    /** A device's correction, ready for the stage: its bands as the stage stores them and the preamp to play them at. */
    data class DeviceCorrection(val bandsJson: String, val preampDb: Float)

    /**
     * «Aplicar automáticamente» off: no correction comes on, but one a headphone put in still goes away on any other
     * output, as the offer promises. Nothing while that headphone (or a hand pick over it) is the output.
     */
    fun planRelease(state: State, outputKey: String): Map<String, Any?> =
        if (state.owner == outputKey || state.owner == USER_PICK_PREFIX + outputKey) emptyMap() else plan(state, outputKey, null)

    fun plan(state: State, outputKey: String, correction: DeviceCorrection?, force: Boolean = false): Map<String, Any?> {
        val edits = LinkedHashMap<String, Any?>()
        val userPick = state.owner?.startsWith(USER_PICK_PREFIX) == true
        val deviceOwner = if (userPick) null else state.owner
        if (correction != null) {
            if (state.owner == USER_PICK_PREFIX + outputKey) return edits
            if (force && (deviceOwner != outputKey || !state.active)) return edits
            if (!force && deviceOwner == outputKey) return edits
            if (deviceOwner == null) {
                edits[KEY_STASH_BANDS] = state.bandsJson
                edits[KEY_STASH_ACTIVE] = state.active
                edits[KEY_STASH_PREAMP] = state.autoPreamp
            }
            // Row 802: the user's own preamp is saved on the OFF → ON transition only.
            if (!state.active) edits[KEY_PREAMP_BEFORE] = state.masterPreamp
            edits[KEY_BANDS] = correction.bandsJson
            edits[KEY_PREAMP] = correction.preampDb
            edits[KEY_MASTER_PREAMP] = correction.preampDb
            edits[KEY_ACTIVE] = true
            edits[KEY_OWNER] = outputKey
            return edits
        }
        if (state.owner == null) return edits
        if (userPick) {
            DEVICE_KEYS.forEach { edits[it] = null }
            return edits
        }
        val restoreActive = state.stashActive == true && state.stashBandsJson != null
        edits[KEY_BANDS] = state.stashBandsJson
        edits[KEY_PREAMP] = state.stashPreamp
        if (restoreActive) {
            edits[KEY_ACTIVE] = true
            edits[KEY_MASTER_PREAMP] = state.stashPreamp ?: (state.preampBefore ?: state.masterPreamp)
            // The user switched the device's correction off by hand (its preamp_before was used up): the preamp they
            // are on now is theirs, and switching their own pick off later must bring it back.
            if (state.preampBefore == null) edits[KEY_PREAMP_BEFORE] = state.masterPreamp
        } else {
            edits[KEY_ACTIVE] = false
            if (state.active) {
                edits[KEY_MASTER_PREAMP] = state.preampBefore ?: state.masterPreamp
                edits[KEY_PREAMP_BEFORE] = null
            }
        }
        DEVICE_KEYS.forEach { edits[it] = null }
        return edits
    }
}
