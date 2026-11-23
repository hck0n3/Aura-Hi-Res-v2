package iad1tya.echo.music.ui.player

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import iad1tya.echo.music.playback.OfflineRejectionException
import iad1tya.echo.music.playback.PlaybackErrorClassifier
import iad1tya.echo.music.ui.player.PlaybackErrorAdvice.Action
import iad1tya.echo.music.ui.player.PlaybackErrorAdvice.Kind
import iad1tya.echo.music.utils.AgeGateChain
import iad1tya.echo.music.utils.YTPlayerUtils
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX audit P22A (registry row 732): the player's error card says what happened in plain words and offers ONE next step,
 * decided from the same classifier onPlayerError routes on. Built with the error SHAPES media3 really delivers (the
 * resolver's exception 2+ levels deep, as in PlaybackErrorClassifierTest).
 */
class PlaybackErrorAdviceTest {

    // Same reflective constructor as PlaybackErrorClassifierTest: the public ones read SystemClock (android.os).
    private fun playbackError(errorCode: Int, message: String? = null, cause: Throwable? = null): PlaybackException {
        val ctor = PlaybackException::class.java.getDeclaredConstructor(
            String::class.java,
            Throwable::class.java,
            Int::class.javaPrimitiveType,
            android.os.Bundle::class.java,
            Long::class.javaPrimitiveType,
        )
        ctor.isAccessible = true
        return ctor.newInstance(message, cause, errorCode, null, 0L)
    }

    private fun invalidResponseCode(code: Int): HttpDataSource.InvalidResponseCodeException {
        val ctor = HttpDataSource.InvalidResponseCodeException::class.java.getDeclaredConstructor(
            Int::class.javaPrimitiveType,
            String::class.java,
            java.io.IOException::class.java,
            Map::class.java,
            androidx.media3.datasource.DataSpec::class.java,
            ByteArray::class.java,
        )
        return ctor.newInstance(code, "http $code", null, emptyMap<String, List<String>>(), null, ByteArray(0))
    }

    /** The resolver's refusal as media3 hands it over: ExoPlaybackException(IO_UNSPECIFIED) → loader wrap → ours. */
    private fun offlineRejection(kind: OfflineRejectionException.Kind): PlaybackException {
        val resolver = playbackError(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            message = "Offline mode: this track is not fully downloaded.",
            cause = OfflineRejectionException(kind),
        )
        return playbackError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, cause = java.io.IOException("Unexpected", resolver))
    }

    /** A NO_STREAM dead end the way StreamResolution maps it (message = reason), wrapped by the loader. */
    private fun noStream(reason: String, contentLevel: Boolean = false, cause: Throwable? = null): PlaybackException {
        val resolution = YTPlayerUtils.StreamResolutionException(reason, cause = cause, contentLevel = contentLevel)
        val mapped = playbackError(PlaybackErrorClassifier.ERROR_CODE_NO_STREAM, message = reason, cause = resolution)
        return playbackError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, cause = java.io.IOException("Unexpected", mapped))
    }

    private fun kind(error: PlaybackException, sessionRejected: Boolean = false) =
        PlaybackErrorAdvice.kindOf(error, sessionRejected)

    @Test
    fun `the manual offline switch says so and offers the offline music`() {
        val error = offlineRejection(OfflineRejectionException.Kind.MANUAL)
        assertEquals(Kind.OFFLINE_MODE, kind(error))
        assertEquals(Action.OPEN_OFFLINE_MUSIC, PlaybackErrorAdvice.actionOf(Kind.OFFLINE_MODE))
        assertEquals("auto_playlist/downloaded", Action.OPEN_OFFLINE_MUSIC.route)
    }

    @Test
    fun `no internet is Sin conexion with Ver musica sin conexion, whichever way it arrives`() {
        assertEquals(Kind.NO_CONNECTION, kind(offlineRejection(OfflineRejectionException.Kind.NO_NETWORK)))
        assertEquals(Kind.NO_CONNECTION, kind(offlineRejection(OfflineRejectionException.Kind.SESSION)))
        // The resolver's UnknownHost, 2 levels deep (StreamResolution maps it to IO_NETWORK_CONNECTION_FAILED).
        val dns = playbackError(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            cause = java.io.IOException(
                "Unexpected",
                playbackError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, cause = java.net.UnknownHostException("h")),
            ),
        )
        assertEquals(Kind.NO_CONNECTION, kind(dns))
        // Every client failed for lack of internet: not an unavailable song (row 690).
        assertEquals(Kind.NO_CONNECTION, kind(noStream("No hay ninguna fuente", cause = java.net.UnknownHostException("youtubei.googleapis.com"))))
        // Offline beats every other reading, a rejected session included.
        assertEquals(Kind.NO_CONNECTION, kind(dns, sessionRejected = true))
        assertEquals(Action.OPEN_OFFLINE_MUSIC, PlaybackErrorAdvice.actionOf(Kind.NO_CONNECTION))
    }

    @Test
    fun `a timeout with the device online is a connection that failed, retried`() {
        val timeout = playbackError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, cause = java.net.SocketTimeoutException("timeout"))
        assertEquals(Kind.UNSTABLE_CONNECTION, kind(timeout))
        assertEquals(Action.RETRY, PlaybackErrorAdvice.actionOf(Kind.UNSTABLE_CONNECTION))
    }

    @Test
    fun `a session the resolver keeps refusing offers Iniciar sesion in Ajustes Cuentas`() {
        val refused = noStream("Este video no está disponible", contentLevel = true)
        assertEquals(Kind.SESSION_EXPIRED, kind(refused, sessionRejected = true))
        assertEquals(Action.SIGN_IN, PlaybackErrorAdvice.actionOf(Kind.SESSION_EXPIRED))
        assertEquals("settings/accounts", Action.SIGN_IN.route)
    }

    @Test
    fun `YouTube's verdict on the song explains region or account and keeps the reason`() {
        val reason = "Este video no está disponible en tu país"
        val region = noStream(reason, contentLevel = true)
        assertEquals(Kind.NOT_AVAILABLE, kind(region))
        assertEquals(reason, PlaybackErrorAdvice.detailOf(region, Kind.NOT_AVAILABLE))
        assertEquals(Action.RETRY, PlaybackErrorAdvice.actionOf(Kind.NOT_AVAILABLE))
    }

    @Test
    fun `an unresolved song that is not a verdict keeps the resolver's reason as detail`() {
        val reason = "La canción tardó demasiado en resolverse"
        val slow = noStream(reason)
        assertEquals(Kind.UNRESOLVED, kind(slow))
        assertEquals(reason, PlaybackErrorAdvice.detailOf(slow, Kind.UNRESOLVED))
        assertNull("only verdict / unresolved kinds carry a detail", PlaybackErrorAdvice.detailOf(slow, Kind.GENERIC))
    }

    @Test
    fun `the age gate keeps its own message and a guest is sent to sign in`() {
        val guest = noStream(AgeGateChain.failureMessage(isLoggedIn = false))
        assertEquals(Kind.AGE_RESTRICTED_GUEST, kind(guest))
        assertEquals(Kind.AGE_RESTRICTED_GUEST, kind(guest, sessionRejected = true))
        assertEquals(Action.SIGN_IN, PlaybackErrorAdvice.actionOf(Kind.AGE_RESTRICTED_GUEST))

        val signedIn = noStream(AgeGateChain.failureMessage(isLoggedIn = true))
        assertEquals(Kind.AGE_RESTRICTED, kind(signedIn))
        assertEquals(Action.RETRY, PlaybackErrorAdvice.actionOf(Kind.AGE_RESTRICTED))

        val youtubeText = playbackError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, message = "Sign in to confirm your age")
        assertEquals(Kind.AGE_RESTRICTED, kind(youtubeText))
    }

    @Test
    fun `a page reload is no longer explained as an age check`() {
        // The card used to test contains("age"): "page" matched it.
        val reload = playbackError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, message = "The page needs to be reloaded")
        assertFalse(PlaybackErrorAdvice.namesAgeCheck("The page needs to be reloaded"))
        assertFalse(PlaybackErrorAdvice.namesAgeCheck("Unexpected message from storage"))
        assertTrue(PlaybackErrorAdvice.namesAgeCheck("Age-restricted video"))
        assertTrue(PlaybackErrorAdvice.namesAgeCheck("Accede para confirmar tu edad"))
        assertEquals(Kind.GENERIC, kind(reload))
    }

    @Test
    fun `a 403 or a bad HTTP status is a rejection, retried`() {
        val forbidden = playbackError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, cause = invalidResponseCode(403))
        assertEquals(Kind.ACCESS_REJECTED, kind(forbidden))
        val server = playbackError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, message = "Server error")
        assertEquals(Kind.ACCESS_REJECTED, kind(server))
        assertEquals(Action.RETRY, PlaybackErrorAdvice.actionOf(Kind.ACCESS_REJECTED))
    }

    @Test
    fun `anything else is the generic sentence with Reintentar`() {
        val decoder = playbackError(PlaybackException.ERROR_CODE_DECODING_FAILED, message = "Decoder failed: c2.android.aac.decoder")
        assertEquals(Kind.GENERIC, kind(decoder))
        assertEquals(Action.RETRY, PlaybackErrorAdvice.actionOf(Kind.GENERIC))
    }

    @Test
    fun `only the two offline kinds and the two sign-in kinds leave the player`() {
        for (k in Kind.entries) {
            val expected = when (k) {
                Kind.OFFLINE_MODE, Kind.NO_CONNECTION -> Action.OPEN_OFFLINE_MUSIC
                Kind.SESSION_EXPIRED, Kind.AGE_RESTRICTED_GUEST -> Action.SIGN_IN
                else -> Action.RETRY
            }
            assertEquals(k.name, expected, PlaybackErrorAdvice.actionOf(k))
        }
        assertNull(Action.RETRY.route)
    }

    // ------------------------------------------------------------------------------------------- wiring guards

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }

    private fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").readText()

    private fun resDirHasString(dir: String, name: String): Boolean =
        File(root, "app/src/main/res/$dir").listFiles { f -> f.name.endsWith(".xml") }.orEmpty()
            .any { it.readText().contains("<string name=\"$name\">") }

    @Test
    fun `the card reads the advice, maps every kind to an ES and EN string and lost the English jargon`() {
        val card = main("ui/player/PlaybackError.kt")
        assertTrue(card.contains("PlaybackErrorAdvice.kindOf(error, sessionRejected = YTPlayerUtils.identityRejections.isIdentityShaped)"))
        assertFalse(card.contains("The server rejected the request"))
        assertTrue("Reintentar survives as the secondary button", card.contains("if (action != PlaybackErrorAdvice.Action.RETRY) {"))
        assertTrue("the error code line is unchanged", card.contains("text = \"Code: \${getErrorCodeName(error.errorCode)} (\${error.errorCode})\""))
        val mapping = Regex("PlaybackErrorAdvice\\.Kind\\.(\\w+) -> R\\.string\\.(\\w+)").findAll(card).associate { it.groupValues[1] to it.groupValues[2] }
        for (k in Kind.entries) {
            val name = mapping[k.name]
            assertTrue("${k.name} has no message", name != null)
            assertTrue("values/ lacks $name", resDirHasString("values", name!!))
            assertTrue("values-es/ lacks $name", resDirHasString("values-es", name))
        }
        for (label in listOf("playback_error_action_offline_music", "playback_error_action_sign_in")) {
            assertTrue(card.contains("R.string.$label"))
            assertTrue(resDirHasString("values", label))
            assertTrue(resDirHasString("values-es", label))
        }
        val es = File(root, "app/src/main/res/values-es/playback_error_strings.xml").readText()
        assertTrue(es.contains("<string name=\"playback_error_action_offline_music\">Ver música sin conexión</string>"))
        assertTrue(es.contains("<string name=\"playback_error_action_sign_in\">Iniciar sesión</string>"))
    }

    @Test
    fun `the next step is wired from the player and its routes exist`() {
        assertTrue(main("ui/player/Thumbnail.kt").contains("onNavigate = onPlaybackErrorNavigate,"))
        val player = main("ui/newui/AuraPlayer.kt")
        val wire = player.indexOf("onPlaybackErrorNavigate = { route ->")
        assertTrue(wire > 0)
        val body = player.substring(wire, wire + 200)
        assertTrue(body.contains("navController.navigateOnce(route)") && body.contains("state.collapseAfterNavigate()"))
        val nav = main("ui/screens/NavigationBuilder.kt")
        assertTrue(nav.contains("composable(\"settings/accounts\")"))
        assertTrue(nav.contains("route = \"auto_playlist/{playlist}\""))
        assertTrue(main("ui/newui/AuraDownloadsScreen.kt").contains("\"downloaded\" -> PlaylistType.DOWNLOAD"))
    }

    @Test
    fun `the service's unavailable notices are resources, not Spanish-only literals`() {
        val recovery = main("playback/PlaybackErrorRecovery.kt")
        assertTrue(recovery.contains("val base = getString(R.string.song_unavailable_notice)"))
        assertFalse(recovery.contains("\"Canción no disponible\""))
        val service = main("playback/MusicService.kt")
        assertTrue(service.contains("getString(R.string.video_unavailable_back_to_audio)"))
        assertFalse(service.contains("\"Video no disponible — volviendo a audio\""))
        for (name in listOf("song_unavailable_notice", "video_unavailable_back_to_audio")) {
            assertTrue(resDirHasString("values", name))
            assertTrue(resDirHasString("values-es", name))
        }
    }

    @Test
    fun `the ERR_BR log line is untouched`() {
        assertTrue(main("playback/MusicService.kt").contains("\"ERR_BR=%s id=%s code=%d connected=%b session=%b validated=%b\""))
    }
}
