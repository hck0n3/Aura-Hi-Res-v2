package iad1tya.echo.music.utils.cipher

/**
 * When is it worth building the cipher WebView BEFORE the first song (plan A2, row 330)?
 *
 * The only consumer that needs it warm before the first play is the PipePipe local decoder tier, and
 * PipePipe only reaches it after [PipePipeLocalCipherDecoder.getPlayerData] succeeds — which it does
 * ONLY when the owner's player table has a verified signatureTimestamp for the CURRENT player hash.
 * For any other hash (YouTube rotated and no config is published yet) the startup WebView was pure
 * cost: a ~2.8 MB player.js analysis, a WebView + renderer process built on Main and kept for the
 * whole session, and an E line "usable for neither sig nor n-transform" in the shared log.
 *
 * Pure (no Android) so it is pinned by JVM tests, and shared with [PipePipeLocalCipherDecoder] so the
 * prewarm gate and the decoder gate can never drift apart.
 */
object CipherWarmPolicy {

    enum class Decision { CREATE, SKIP_NO_PLAYER_JS, SKIP_NO_VERIFIED_CONFIG }

    /** A usable signatureTimestamp, or null (missing / non-positive = not verified; never guessed). */
    fun verifiedSts(sts: Int?): Int? = sts?.takeIf { it > 0 }

    fun decide(playerHash: String?, verifiedSts: Int?): Decision = when {
        playerHash.isNullOrBlank() -> Decision.SKIP_NO_PLAYER_JS
        verifiedSts == null -> Decision.SKIP_NO_VERIFIED_CONFIG
        else -> Decision.CREATE
    }
}
