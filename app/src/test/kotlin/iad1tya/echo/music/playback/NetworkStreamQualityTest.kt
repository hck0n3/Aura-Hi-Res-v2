package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 811 (owner H6b, 2026-11-24): one streaming quality for Wi-Fi and another for mobile data. */
class NetworkStreamQualityTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved", it.isFile) }.readText()

    private val noNetworkRead: () -> NetworkStreamQuality.Network = { error("the network must not be read when both agree") }

    @Test
    fun `nothing stored keeps the full pick and never reads the network`() {
        assertFalse(NetworkStreamQuality.wantsSaver(null, null, noNetworkRead))
        assertEquals(NetworkStreamQuality.Level.HIGH, NetworkStreamQuality.parse(null))
        assertEquals(NetworkStreamQuality.Level.HIGH, NetworkStreamQuality.parse("not-a-level"))
    }

    @Test
    fun `the same choice on both networks needs no network read`() {
        assertTrue(NetworkStreamQuality.wantsSaver("SAVER", "SAVER", noNetworkRead))
        assertFalse(NetworkStreamQuality.wantsSaver("HIGH", null, noNetworkRead))
    }

    @Test
    fun `each network gets its own choice`() {
        val mobile = { NetworkStreamQuality.Network.MOBILE }
        val other = { NetworkStreamQuality.Network.OTHER }
        assertTrue(NetworkStreamQuality.wantsSaver("HIGH", "SAVER", mobile))
        assertFalse(NetworkStreamQuality.wantsSaver("HIGH", "SAVER", other))
        assertFalse(NetworkStreamQuality.wantsSaver("SAVER", "HIGH", mobile))
        assertTrue(NetworkStreamQuality.wantsSaver("SAVER", "HIGH", other))
        // Absent mobile = HIGH, like before the setting existed.
        assertFalse(NetworkStreamQuality.wantsSaver("SAVER", null, mobile))
    }

    @Test
    fun `only the listening resolves opt in, and the saver pick is the Data Saver one`() {
        val yt = main("utils/YTPlayerUtils.kt")
        assertTrue(yt.contains("networkAwareQuality: Boolean = false,"))
        assertTrue(yt.contains("val saverAudio = networkAwareQuality &&"))
        assertTrue(yt.contains("val dataSaverOn = PrefsBridge.peek(iad1tya.echo.music.constants.DataSaverEnabledKey) == true || saverAudio"))
        assertTrue(yt.contains("saverAudio = saverAudio,"))

        assertEquals(2, Regex("networkAwareQuality = true,").findAll(main("playback/StreamResolution.kt")).count())
        assertEquals(1, Regex("networkAwareQuality = true,").findAll(main("playback/MusicService.kt")).count())
        // Downloads keep their quality whatever network fetched them.
        assertFalse(main("playback/DownloadUtil.kt").contains("networkAwareQuality"))
    }

    @Test
    fun `the network is read at resolve time only - no callback, no polling`() {
        val q = main("playback/NetworkStreamQuality.kt")
        assertFalse(q.contains("registerNetworkCallback"))
        assertFalse(q.contains("delay("))
        assertFalse(q.contains("while ("))
        assertTrue(q.contains("val WifiKey = stringPreferencesKey(\"streamQualityWifi\")"))
        assertTrue(q.contains("val MobileKey = stringPreferencesKey(\"streamQualityMobile\")"))
    }

    @Test
    fun `both rows are in the Data Saver group and indexed`() {
        val settings = main("ui/screens/settings/PlayerSettings.kt")
        assertTrue(settings.contains("title = { Text(stringResource(R.string.stream_quality_wifi)) },"))
        assertTrue(settings.contains("title = { Text(stringResource(R.string.stream_quality_mobile)) },"))
        val index = main("ui/screens/settings/SearchableSettings.kt")
        assertTrue(index.contains("Triple(stringResource(R.string.stream_quality_wifi), \"Reproductor\", \"settings/player\"),"))
        assertTrue(index.contains("Triple(stringResource(R.string.stream_quality_mobile), \"Reproductor\", \"settings/player\"),"))
    }
}
