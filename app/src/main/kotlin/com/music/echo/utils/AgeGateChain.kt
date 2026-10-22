package iad1tya.echo.music.utils

/**
 * Pure rules of the age-gate recovery in [YTPlayerUtils] (owner 2026-10-19: *«necesito que Aura utilice mi cuenta para
 * reproducir y evitar esto sí o sí, en vez de decir que el contenido no existe»*).
 *
 * What the order is based on (yt-dlp master, 2026 — `_video.py` / `_base.py`):
 *  - an age-gated video is retried with `web_embedded` (WEB_EMBEDDED_PLAYER, id 56, supports cookies, any non-YouTube
 *    `embedUrl`), and, for a SIGNED-IN session, with `web_creator` (needs the account: REQUIRE_AUTH);
 *  - the default clients of a signed-in session are `web_embedded`, `tv_samsung` (TVHTML5, supports cookies) and `web`;
 *  - `web_creator`, `web_music` and `mweb` need a GVS PO token for the stream itself (`required=True`), `web_embedded` and
 *    `tv_samsung` do not, so they go first and `web_creator` last.
 * The old TV embedded client (TVHTML5_SIMPLY_EMBEDDED_PLAYER, id 85) no longer exists in yt-dlp, so it is the last resort.
 *
 * The trigger is the STATUS, never the reason text: YouTube localises it («Accede para confirmar tu edad»).
 */
internal object AgeGateChain {

    enum class Step {
        WEB_EMBEDDED, TV_SAMSUNG, WEB_CREATOR,

        /**
         * WEB_REMIX — the YouTube Music web client itself, asked with the account's cookies and SAPISIDHASH. It is what the
         * official YouTube Music plays an age-gated song with once the user is signed in (owner 2026-10-19), and what SimpMusic
         * sends first for every song (core `Ytmusic.player`, `ytClient(client, setLogin = true)`). Only a signed-in session can
         * use it, and its streams need a GVS PO token like web_creator, so it goes after the clients that do not.
         */
        ACCOUNT_WEB_REMIX,
        LEGACY_TV_EMBEDDED,
    }

    /** Statuses that name the age gate directly. */
    val AGE_STATUSES = setOf("AGE_CHECK_REQUIRED", "AGE_VERIFICATION_REQUIRED", "CONTENT_CHECK_REQUIRED")

    /**
     * YouTube answers an age-gated video to the ANDROID_VR main client with LOGIN_REQUIRED, not with an AGE_* status, and a
     * bot-check answers LOGIN_REQUIRED too — the two cannot be told apart without the localised text, so both get the
     * recovery (a bot-check costs a few extra requests and ends exactly as before).
     */
    fun isAgeGateShaped(status: String?, desktopLegacyAgeGateReason: Int? = null): Boolean =
        desktopLegacyAgeGateReason != null || (status != null && (status == "LOGIN_REQUIRED" || status in AGE_STATUSES))

    /**
     * Steps whose stream URLs need a GVS PO token (yt-dlp `GVS_PO_TOKEN_POLICY.required`: web_creator and web_music). Without
     * a token they would hand back a response whose URLs 403 and, being accepted first, stop the chain — so the step is
     * skipped instead, and the clients that need no token (web_embedded, tv_samsung) still get their turn.
     */
    fun needsStreamPoToken(step: Step): Boolean = step == Step.WEB_CREATOR || step == Step.ACCOUNT_WEB_REMIX

    /** The steps to try, in order. [alreadyTried] are skipped. A guest has no account, so no creator client for them. */
    fun order(isLoggedIn: Boolean, alreadyTried: Set<Step> = emptySet()): List<Step> {
        val all = if (isLoggedIn) {
            listOf(Step.WEB_EMBEDDED, Step.TV_SAMSUNG, Step.WEB_CREATOR, Step.ACCOUNT_WEB_REMIX, Step.LEGACY_TV_EMBEDDED)
        } else {
            listOf(Step.WEB_EMBEDDED, Step.TV_SAMSUNG, Step.LEGACY_TV_EMBEDDED)
        }
        return all.filter { it !in alreadyTried }
    }

    /**
     * What the user reads when every step failed, instead of «la canción no está disponible» / «no existe»: it was never
     * the song that was missing.
     */
    fun failureMessage(isLoggedIn: Boolean): String =
        if (isLoggedIn) {
            "YouTube pide confirmar la edad y no la ha confirmado con tu cuenta. Entra en youtube.com con esta misma cuenta, " +
                "verifica tu edad y vuelve a intentarlo (puede que también haga falta cerrar sesión y volver a entrar en Aura)."
        } else {
            "Esta canción tiene restricción de edad. Inicia sesión con una cuenta de YouTube de un adulto " +
                "(Ajustes ▸ Cuenta) para reproducirla."
        }
}
