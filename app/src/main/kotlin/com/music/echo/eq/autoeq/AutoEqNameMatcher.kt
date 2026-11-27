package iad1tya.echo.music.eq.autoeq

/**
 * Finds the AutoEq measurement for a Bluetooth device name (registry row 915). Pure and local: no network, no logging
 * (the name can carry the owner's own name, «AirPods Pro de Ana»).
 *
 * How a name matches a model:
 *  1. Both sides are reduced to lowercase letter/digit tokens. A catalogue model keeps its bracketed variant apart
 *     («Sony WH-1000XM4 (ANC on)» → model «sony wh 1000xm4», variant «anc on»).
 *  2. A model matches when its tokens, glued together, equal a run of WHOLE device tokens glued together — with or
 *     without the brand (first token), so «WH-1000XM4» finds «Sony WH-1000XM4» and «Buds2 Pro» spacing differences do
 *     not matter. Every device token after the run must be noise (a hex id like Samsung's «(A1B2)», «LE», «Hands-Free»…)
 *     or start the owner's name («AirPods Pro de Ana»): «Galaxy Buds2 Pro» can never fall back to «Galaxy Buds2», and
 *     «WF-1000XM4» never matches «WH-1000XM4». Tokens BEFORE the run are allowed, because that is where a phone puts
 *     the owner in English («Juan's AirPods Pro»).
 *  3. The longest match wins (with the brand beats without). Two different models tied → no match: never guess.
 *     Nor when the winner has a numbered successor in the catalogue («AirPods Pro» / «AirPods Pro 2», «Galaxy Buds» /
 *     «Galaxy Buds2»): Apple gives both generations the same Bluetooth name, so a name without the number does not
 *     say which one it is.
 *  4. Among that model's measurements: AutoEq's own recommended one first (its most accurate source), then a neutral
 *     variant (none, «ANC on», «wireless»…), then oratory1990 › crinacle › Rtings › the rest. Variants that are a preset,
 *     an ear tip, a volume level or a switch setting are never picked automatically.
 *  5. The rig type follows the name when it says so («Buds», «WF-», «IEM» = in the ear; «WH-», «Headphones» = over the
 *     ear), so an in-ear model is not corrected with an over-ear measurement of the same name.
 */
object AutoEqNameMatcher {

    /** A model name shorter than this (glued) is too generic to match on its own («Pro», «X1»). */
    private const val MIN_KEY_LENGTH = 5

    /** Device tokens that may follow the model without changing which model it is. */
    private val NOISE_TAIL = setOf(
        "le", "ble", "hands", "free", "handsfree", "stereo", "audio", "hd", "a2dp", "bt", "bluetooth", "wireless",
        "headset", "headphones", "headphone", "earbuds", "earphones", "tws", "l", "r", "left", "right",
    )

    /** Catalogue variants that describe the normal Bluetooth use and may be picked automatically. */
    private val NEUTRAL_VARIANTS = setOf(
        "anc on", "anc mode", "anc", "wireless", "bluetooth", "bt", "sample 1", "noise cancelling", "nc on",
    )

    private val SOURCE_RANK = listOf("oratory1990", "crinacle", "Rtings")

    data class Match(val entry: AutoEqOfflineEntry, val model: String)

    fun tokens(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    /** «Sony WH-1000XM4 (ANC on)» → «Sony WH-1000XM4» and «anc on». */
    fun splitVariant(name: String): Pair<String, String> {
        val open = name.indexOf('(')
        if (open <= 0) return name.trim() to ""
        return name.substring(0, open).trim() to tokens(name.substring(open)).joinToString(" ")
    }

    /** «de», «of», «von»…: what follows is the owner's name («AirPods Pro de Ana»), never part of the model. */
    private val OWNER_MARKERS = setOf("de", "di", "da", "do", "del", "von", "van", "of")

    /** A tail token that names no model: a known word, or a 4-6 character hex id («5f3c»). A lone «2» is NOT noise. */
    private fun isNoise(token: String): Boolean =
        token in NOISE_TAIL ||
            (token.length in 4..6 && token.any { it.isDigit() } && token.all { it in '0'..'9' || it in 'a'..'f' })

    /** The device tokens after the model may only be noise, or an owner marker followed by anything. */
    private fun tailIsNoise(tail: List<String>): Boolean {
        for (t in tail) {
            if (t in OWNER_MARKERS) return true
            if (!isNoise(t)) return false
        }
        return true
    }

    /**
     * Length of the longest glued [key] that equals whole device tokens `[i, j)` followed only by noise; 0 when none.
     * Hex-looking tokens are only noise AFTER the run, never inside it («1000xm4» is part of the model).
     */
    private fun matchLength(device: List<String>, key: String): Int {
        if (key.length < MIN_KEY_LENGTH) return 0
        for (i in device.indices) {
            val sb = StringBuilder()
            for (j in i until device.size) {
                sb.append(device[j])
                if (sb.length > key.length) break
                if (sb.length == key.length) {
                    if (sb.toString() == key && tailIsNoise(device.subList(j + 1, device.size))) return key.length
                    break
                }
            }
        }
        return 0
    }

    private fun hintedType(device: List<String>): AutoEqRigType? {
        val inEar = device.any { it.startsWith("buds") || it == "wf" || it == "iem" || it == "earbuds" || it.endsWith("buds") }
        val overEar = device.any { it == "wh" || it == "headphones" || it == "headphone" }
        return when {
            inEar && !overEar -> AutoEqRigType.IN_EAR
            overEar && !inEar -> AutoEqRigType.OVER_EAR
            else -> null
        }
    }

    private fun typeFits(type: AutoEqRigType, hint: AutoEqRigType?): Boolean = when (hint) {
        null -> true
        AutoEqRigType.IN_EAR -> type == AutoEqRigType.IN_EAR || type == AutoEqRigType.EARBUD
        else -> type == hint
    }

    private fun sourceRank(source: String): Int = SOURCE_RANK.indexOf(source).let { if (it < 0) SOURCE_RANK.size else it }

    fun match(deviceName: String, catalogue: List<AutoEqOfflineEntry>): Match? {
        val device = tokens(deviceName)
        if (device.isEmpty()) return null
        // Best glued length per model, scoring the full name above the brand-less one.
        val scored = HashMap<String, Int>()
        val modelKeys = HashSet<String>()
        for (entry in catalogue) {
            val model = splitVariant(entry.name).first
            if (model in scored) continue
            val t = tokens(model)
            if (t.isEmpty()) continue
            modelKeys += t.joinToString("")
            val full = matchLength(device, t.joinToString(""))
            val score = if (full > 0) full * 2 + 1 else if (t.size > 1) matchLength(device, t.drop(1).joinToString("")) * 2 else 0
            scored[model] = score
        }
        val best = scored.values.maxOrNull() ?: 0
        if (best <= 0) return null
        val winners = scored.filterValues { it == best }.keys
        // Two catalogue spellings of one model («AirPods Pro 2» / «Airpods Pro 2») are the same model, not a tie.
        val winnerKeys = winners.map { tokens(it).joinToString("") }.toSet()
        if (winnerKeys.size != 1) return null
        if (('2'..'9').any { winnerKeys.first() + it in modelKeys }) return null
        val hint = hintedType(device)
        val candidates = catalogue.filter { e ->
            val (model, variant) = splitVariant(e.name)
            model in winners && (variant.isEmpty() || variant in NEUTRAL_VARIANTS) && typeFits(e.type, hint)
        }
        val pick = candidates.minWithOrNull(
            compareBy<AutoEqOfflineEntry>(
                { !it.recommended },
                { splitVariant(it.name).second.isNotEmpty() },
                { sourceRank(it.source) },
                { it.source.lowercase() },
            ),
        ) ?: return null
        return Match(pick, splitVariant(pick.name).first)
    }
}
