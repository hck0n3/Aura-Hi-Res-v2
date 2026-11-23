package iad1tya.echo.music.ui.player

import androidx.media3.common.PlaybackException
import iad1tya.echo.music.playback.OfflineRejectionException
import iad1tya.echo.music.playback.PlaybackErrorClassifier
import iad1tya.echo.music.utils.AgeGateChain

/**
 * What the player's error card says in plain words, and the ONE next step it offers (UX audit P22A).
 *
 * Pure: it reads the error only through [PlaybackErrorClassifier] — the same predicates onPlayerError routes on — plus
 * one live flag the caller passes in, so every branch is pinned by PlaybackErrorAdviceTest. It decides the sentence and
 * the button; the ERR_BR log line, the recovery routing and the «Code: …» line of the card are not touched.
 */
object PlaybackErrorAdvice {

    enum class Kind {
        /** The resolver refused the song because the user's manual offline switch is on. */
        OFFLINE_MODE,

        /** No internet: an offline refusal (no network / offline session) or a real connectivity cause in the chain. */
        NO_CONNECTION,

        /** Network-shaped (timeout, a server that stopped answering) while the device itself is not proven offline. */
        UNSTABLE_CONNECTION,

        /** Age gate the signed-in account could not pass (AgeGateChain message for a session, or YouTube's own text). */
        AGE_RESTRICTED,

        /** Age gate met with no account signed in: the way out is signing in. */
        AGE_RESTRICTED_GUEST,

        /** The resolver sees a streak of DISTINCT videos refused for this session (IdentityRejectionTracker). */
        SESSION_EXPIRED,

        /** YouTube's own verdict on the SONG: region, Premium/members-only, removed. */
        NOT_AVAILABLE,

        /** A 403 / bot check / LOGIN_REQUIRED / bad HTTP status that is not one of the above. */
        ACCESS_REJECTED,

        /** No client could serve the song for another reason (timed out, no format, no URL). */
        UNRESOLVED,

        GENERIC,
    }

    /** The next step. [route] is a destination of the app's NavHost; null means "re-prepare the player". */
    enum class Action(val route: String?) {
        RETRY(null),

        /** «Descargadas» — the same destination as the Home tile and the Library shortcut. */
        OPEN_OFFLINE_MUSIC("auto_playlist/downloaded"),

        /** Ajustes ▸ Cuentas — where the YouTube session is signed out and in (session_expired_sign_in_again names it). */
        SIGN_IN("settings/accounts"),
    }

    fun actionOf(kind: Kind): Action = when (kind) {
        Kind.OFFLINE_MODE, Kind.NO_CONNECTION -> Action.OPEN_OFFLINE_MUSIC
        Kind.SESSION_EXPIRED, Kind.AGE_RESTRICTED_GUEST -> Action.SIGN_IN
        Kind.UNSTABLE_CONNECTION, Kind.AGE_RESTRICTED, Kind.NOT_AVAILABLE, Kind.ACCESS_REJECTED,
        Kind.UNRESOLVED, Kind.GENERIC -> Action.RETRY
    }

    /** The text the card has always read the cause from: two cause levels down first, as before. */
    fun rawMessage(error: PlaybackException): String? =
        error.cause?.cause?.message ?: error.cause?.message ?: error.message

    /**
     * @param sessionRejected `YTPlayerUtils.identityRejections.isIdentityShaped` at the moment the card is drawn.
     */
    fun kindOf(error: PlaybackException, sessionRejected: Boolean): Kind {
        when (PlaybackErrorClassifier.offlineRejectionKind(error)) {
            OfflineRejectionException.Kind.MANUAL -> return Kind.OFFLINE_MODE
            OfflineRejectionException.Kind.NO_NETWORK, OfflineRejectionException.Kind.SESSION -> return Kind.NO_CONNECTION
            null -> Unit
        }
        if (PlaybackErrorClassifier.isConnectivityFailure(error)) return Kind.NO_CONNECTION

        val noStream = PlaybackErrorClassifier.isNoStreamError(error)
        val reason = if (noStream) PlaybackErrorClassifier.noStreamReason(error) else null
        if (reason == AgeGateChain.failureMessage(isLoggedIn = false)) return Kind.AGE_RESTRICTED_GUEST
        val raw = rawMessage(error).orEmpty()
        if (reason == AgeGateChain.failureMessage(isLoggedIn = true) || namesAgeCheck(raw)) return Kind.AGE_RESTRICTED

        if (sessionRejected) return Kind.SESSION_EXPIRED
        if (PlaybackErrorClassifier.isContentLevelNoStream(error)) return Kind.NOT_AVAILABLE
        if (looksRejected(error, raw)) return Kind.ACCESS_REJECTED
        if (PlaybackErrorClassifier.isNetworkRelatedError(error)) return Kind.UNSTABLE_CONNECTION
        if (noStream) return Kind.UNRESOLVED
        return Kind.GENERIC
    }

    /**
     * The resolver's own reason, shown under the plain sentence for the two kinds where it is a human sentence (YouTube's
     * localised verdict, or Aura's «La canción tardó demasiado en resolverse»). Null for every other kind.
     */
    fun detailOf(error: PlaybackException, kind: Kind): String? =
        if (kind == Kind.NOT_AVAILABLE || kind == Kind.UNRESOLVED) {
            PlaybackErrorClassifier.noStreamReason(error)?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        }

    /**
     * "age" / "edad" as a WORD. The card used to test `contains("age")`, which also matched "page", "message" and
     * "storage" — a «page needs to be reloaded» error was explained as an age check.
     */
    private val AGE_WORD = Regex("(?i)(^|[^\\p{L}])(age|edad)([^\\p{L}]|$)")

    internal fun namesAgeCheck(raw: String): Boolean = AGE_WORD.containsMatchIn(raw)

    /** The card's previous "server rejected the request" test, plus the classifier's 403 walk of the whole chain. */
    private fun looksRejected(error: PlaybackException, raw: String): Boolean =
        PlaybackErrorClassifier.isExpiredUrlError(error) ||
            raw.contains("LOGIN_REQUIRED", ignoreCase = true) ||
            raw.contains("bot", ignoreCase = true) ||
            raw.contains("403") ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
}
