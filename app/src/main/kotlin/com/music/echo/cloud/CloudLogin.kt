package iad1tya.echo.music.cloud

import java.net.URI

/*
 * Registry row 890 — signing in to OneDrive / Dropbox, pure part (tested on the JVM).
 *
 * Owner decisions (plan, phase 5): only OneDrive and Dropbox; rclone's own shared app ids (no keys made by users, no app
 * verification); the provider's own button in a browser tab; read-only.
 *
 * The flow, as rclone v1.76.0 does it through rc (lib/oauthutil/oauthutil.go, fs/backend_config.go, fs/config/config.go):
 *  1. `config/create` with `_async` and `nonInteractive`. The answers in the parameters are rclone's "choices":
 *     `config_is_local=true` (use the local webserver), `config_auth_no_browser=true` (do not try to open a browser
 *     itself) and, for OneDrive, the account type and the drive confirmation. rclone starts its OAuth webserver on
 *     127.0.0.1:53682 and the job blocks there.
 *  2. `config/oauthstatus` then reports `running` and `authUrl` = `http://127.0.0.1:53682/auth?state=…`, which redirects
 *     to the provider's sign-in page. Aura opens THAT url in a browser tab and waits (bounded, cancellable).
 *  3. The provider redirects back to http://localhost:53682/, rclone exchanges the code, saves the token, and the job
 *     finishes. OneDrive may stop at one more question (which drive): answered with `config/update` + `continue`.
 *  4. `operations/list` on the root proves the account works. Any failure deletes the half-made remote.
 */

enum class CloudProvider(val remoteName: String, val type: String, val label: String) {
    ONEDRIVE("aura-onedrive", "onedrive", "OneDrive"),
    DROPBOX("aura-dropbox", "dropbox", "Dropbox");

    companion object {
        fun ofRemote(name: String): CloudProvider? = entries.firstOrNull { it.remoteName == name }
    }
}

/** Why a cloud action did not work, as the page words it. Never carries rclone's text. */
enum class CloudFailure { NO_BINARY, NO_NETWORK, CANCELLED, TIMEOUT, DENIED, RECONNECT, OTHER }

sealed interface CloudLoginState {
    data object Idle : CloudLoginState
    data class Starting(val provider: CloudProvider) : CloudLoginState
    /** [authUrl] is rclone's local loopback page; [toString] leaves it out so it can never reach a log. */
    data class WaitingForBrowser(val provider: CloudProvider, val authUrl: String) : CloudLoginState {
        override fun toString() = "WaitingForBrowser($provider)"
    }
    data class Finishing(val provider: CloudProvider) : CloudLoginState
    data class Connected(val provider: CloudProvider) : CloudLoginState
    data class Failed(val provider: CloudProvider, val reason: CloudFailure) : CloudLoginState

    val busy: Boolean get() = this is Starting || this is WaitingForBrowser || this is Finishing
}

sealed interface CloudLoginEvent {
    data class Start(val provider: CloudProvider) : CloudLoginEvent
    data class AuthUrl(val url: String) : CloudLoginEvent {
        override fun toString() = "AuthUrl"
    }
    /** rclone's job ended well: the token is saved (maybe with a question left to answer). */
    data object BrowserDone : CloudLoginEvent
    data object Verified : CloudLoginEvent
    data class Fail(val reason: CloudFailure) : CloudLoginEvent
    data object Cancel : CloudLoginEvent
    data object Timeout : CloudLoginEvent
}

object CloudLoginMachine {
    fun reduce(state: CloudLoginState, event: CloudLoginEvent): CloudLoginState = when (event) {
        // One sign-in at a time.
        is CloudLoginEvent.Start -> if (state.busy) state else CloudLoginState.Starting(event.provider)
        is CloudLoginEvent.AuthUrl -> when (state) {
            is CloudLoginState.Starting ->
                if (CloudLoginPolicy.isLoopbackAuthUrl(event.url)) CloudLoginState.WaitingForBrowser(state.provider, event.url)
                else CloudLoginState.Failed(state.provider, CloudFailure.OTHER)
            else -> state
        }
        CloudLoginEvent.BrowserDone -> when (state) {
            is CloudLoginState.Starting -> CloudLoginState.Finishing(state.provider)
            is CloudLoginState.WaitingForBrowser -> CloudLoginState.Finishing(state.provider)
            else -> state
        }
        CloudLoginEvent.Verified -> if (state is CloudLoginState.Finishing) CloudLoginState.Connected(state.provider) else state
        is CloudLoginEvent.Fail -> failed(state, event.reason)
        CloudLoginEvent.Cancel -> failed(state, CloudFailure.CANCELLED)
        CloudLoginEvent.Timeout -> when (state) {
            is CloudLoginState.Starting, is CloudLoginState.WaitingForBrowser -> failed(state, CloudFailure.TIMEOUT)
            else -> state
        }
    }

    private fun failed(state: CloudLoginState, reason: CloudFailure): CloudLoginState = when (state) {
        is CloudLoginState.Starting -> CloudLoginState.Failed(state.provider, reason)
        is CloudLoginState.WaitingForBrowser -> CloudLoginState.Failed(state.provider, reason)
        is CloudLoginState.Finishing -> CloudLoginState.Failed(state.provider, reason)
        else -> state
    }
}

/** What the page shows for an account rclone has: it works, it needs signing in again, or it could not be checked. */
enum class CloudAccountStatus { CONNECTED, RECONNECT, UNCHECKED }

object CloudLoginPolicy {
    /** How long a sign-in may wait for the browser before it is abandoned. */
    const val OAUTH_TIMEOUT_MS = 5 * 60_000L

    /** How long rclone may take to bring its OAuth webserver up. */
    const val AUTH_URL_WAIT_MS = 20_000L

    /** Step of the bounded wait (only while a sign-in runs on the cloud page). */
    const val POLL_MS = 1_500L

    /** Questions rclone may still ask after the browser (OneDrive: which drive); more is a loop, and fails. */
    const val MAX_QUESTIONS = 4

    /**
     * OneDrive read-only scopes (rclone's `access_scopes`; v1.76.0 lists "Files.Read Files.Read.All Sites.Read.All
     * offline_access" as its read-only example, minus SharePoint sites, which Aura never browses). Dropbox: rclone's shared
     * Dropbox app has no read-only scope option, so the read-only promise there is Aura's own [RcAllowList].
     */
    const val ONEDRIVE_READ_ONLY_SCOPES = "Files.Read Files.Read.All offline_access"

    const val OAUTH_HOST = "127.0.0.1"
    const val OAUTH_PORT = 53682

    fun createParameters(provider: CloudProvider): Map<String, String> {
        val common = mapOf("config_is_local" to "true", "config_auth_no_browser" to "true")
        return when (provider) {
            CloudProvider.ONEDRIVE -> common + mapOf(
                "access_scopes" to ONEDRIVE_READ_ONLY_SCOPES,
                "config_type" to "onedrive",
                "config_drive_ok" to "true",
            )
            CloudProvider.DROPBOX -> common
        }
    }

    /** Only rclone's own local page is ever opened: `http://127.0.0.1:53682/auth?state=…`. */
    fun isLoopbackAuthUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "http" && uri.host == OAUTH_HOST && uri.port == OAUTH_PORT && uri.path == "/auth" &&
            uri.rawQuery.orEmpty().startsWith("state=") && uri.rawUserInfo == null
    }

    /** The answer to a question rclone stopped at, or null when Aura has none (the sign-in then fails, never loops). */
    fun answer(provider: CloudProvider, question: RcResponses.ConfigQuestion): String? = when (question.name) {
        "config_is_local", "config_drive_ok" -> "true"
        "config_type" -> if (provider == CloudProvider.ONEDRIVE) "onedrive" else null
        "config_driveid" -> pickDrive(question.examples)
        else -> null
    }

    /** The user's own OneDrive (personal or business) before any SharePoint library. */
    fun pickDrive(examples: List<Pair<String, String>>): String? =
        (examples.firstOrNull { (_, help) -> help.endsWith("(personal)") || help.endsWith("(business)") } ?: examples.firstOrNull())
            ?.first?.takeIf { it.isNotEmpty() }

    /** rclone's error text → what the page says. The text itself is never shown or logged. */
    fun classify(rcError: String): CloudFailure {
        val e = rcError.lowercase()
        return when {
            "oauth authentication was cancelled" in e -> CloudFailure.CANCELLED
            "access_denied" in e || "access denied" in e || "user_cancelled" in e -> CloudFailure.DENIED
            RECONNECT_MARKERS.any { it in e } -> CloudFailure.RECONNECT
            NETWORK_MARKERS.any { it in e } -> CloudFailure.NO_NETWORK
            else -> CloudFailure.OTHER
        }
    }

    fun statusOf(failure: CloudFailure?): CloudAccountStatus = when (failure) {
        null -> CloudAccountStatus.CONNECTED
        CloudFailure.RECONNECT, CloudFailure.DENIED -> CloudAccountStatus.RECONNECT
        else -> CloudAccountStatus.UNCHECKED
    }

    private val RECONNECT_MARKERS = listOf(
        "invalid_grant", "token expired", "token has been expired", "revoked", "unauthorized", "401",
        "couldn't fetch token", "empty token", "expired_access_token", "invalid_access_token", "aadsts",
        "interaction_required", "reauth",
    )
    private val NETWORK_MARKERS = listOf(
        "no such host", "network is unreachable", "i/o timeout", "dial tcp", "connection reset",
        "tls handshake timeout", "context deadline exceeded", "temporary failure in name resolution",
    )
}
