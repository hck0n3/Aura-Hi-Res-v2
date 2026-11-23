package iad1tya.echo.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Row 676 (owner 2026-11-23): by default Aura only opens YouTube, YouTube Music and song.link links; every other
 * service is its own activity-alias, DISABLED in the manifest, switched on per service from Ajustes ▸ Contenido.
 */
class OpenLinkServicesTest {
    private val manifest by lazy { File("src/main/AndroidManifest.xml").readText() }

    private fun aliasBlock(aliasClass: String): String {
        val name = "android:name=\"." + aliasClass.substringAfterLast('.') + "\""
        val start = manifest.indexOf(name)
        assertTrue("$aliasClass is not declared in the manifest", start > 0)
        return manifest.substring(start, manifest.indexOf("</activity-alias>", start))
    }

    /** MainActivity's own declaration: everything before its closing tag. */
    private val mainActivityBlock by lazy {
        val start = manifest.indexOf("android:name=\".MainActivity\"")
        manifest.substring(start, manifest.indexOf("</activity>", start))
    }

    @Test
    fun everyExternalServiceIsADisabledAliasWithItsOwnHosts() {
        for (service in OpenLinkServices.Service.entries) {
            val block = aliasBlock(service.aliasClass)
            assertTrue("${service.name} must be off by default", block.contains("android:enabled=\"false\""))
            assertTrue(block.contains("android:targetActivity=\".MainActivity\""))
            for (host in service.hosts) {
                assertTrue("${service.name}: $host", block.contains("android:host=\"$host\""))
            }
        }
        assertTrue("spotify: URIs follow the Spotify switch", aliasBlock(OpenLinkServices.Service.SPOTIFY.aliasClass).contains("android:scheme=\"spotify\""))
    }

    @Test
    fun mainActivityOnlyKeepsYouTubeAndSongLink() {
        for (service in OpenLinkServices.Service.entries) {
            for (host in service.hosts) {
                assertFalse("$host would open in Aura with its switch off", mainActivityBlock.contains("android:host=\"$host\""))
            }
        }
        assertFalse(mainActivityBlock.contains("android:scheme=\"spotify\""))
        for (host in OpenLinkServices.YOUTUBE_HOSTS + OpenLinkServices.SONG_LINK_HOSTS) {
            assertTrue("$host must stay always on", mainActivityBlock.contains("android:host=\"$host\""))
        }
    }

    @Test
    fun everyHostTheResolverUnderstandsHasAPlaceInTheSettings() {
        // The hosts ExternalMusicLinks.isExternalMusicLink accepts: each one is either always on (song.link) or
        // belongs to exactly one service switch.
        val resolver = File("src/main/kotlin/com/music/echo/utils/ExternalMusicLinks.kt").readText()
            .substringAfter("fun isExternalMusicLink(uri: Uri): Boolean {").substringBefore("\n    }\n")
        val shortHosts = File("src/main/kotlin/com/music/echo/utils/ExternalMusicLinks.kt").readText()
            .substringAfter("private val SHORT_HOSTS = setOf(").substringBefore(")")
        val hosts = (Regex("\"([a-z0-9.]+\\.[a-z]+)\"").findAll(resolver) + Regex("\"([a-z0-9.]+)\"").findAll(shortHosts))
            .map { it.groupValues[1] }.toSet()
        assertTrue(hosts.contains("open.spotify.com"))
        val owned = OpenLinkServices.Service.entries.flatMap { it.hosts } + OpenLinkServices.SONG_LINK_HOSTS
        for (host in hosts) assertTrue("$host has no switch", host in owned)
        assertEquals("a host belongs to one switch only", owned.size, owned.toSet().size)
    }

    @Test
    fun theSystemStateIsSummarisedHonestly() {
        assertEquals(OpenLinkServices.LinkState.ALL, OpenLinkServices.summarize(listOf(true, true), linkHandlingAllowed = true))
        assertEquals(OpenLinkServices.LinkState.SOME, OpenLinkServices.summarize(listOf(true, false), linkHandlingAllowed = true))
        assertEquals(OpenLinkServices.LinkState.NONE, OpenLinkServices.summarize(listOf(false, false), linkHandlingAllowed = true))
        // «Abrir enlaces compatibles» off for the whole app: nothing opens, whatever each domain says.
        assertEquals(OpenLinkServices.LinkState.NONE, OpenLinkServices.summarize(listOf(true, true), linkHandlingAllowed = false))
        assertEquals(OpenLinkServices.LinkState.UNKNOWN, OpenLinkServices.summarize(emptyList(), linkHandlingAllowed = true))
    }

    @Test
    fun theSwitchesReadAndWriteTheAliasItself() {
        val ui = File("src/main/kotlin/com/music/echo/ui/screens/settings/ContentSettings.kt").readText()
        assertTrue("the rows live in the «Abrir enlaces» group", ui.contains("+ openLinkServiceItems()"))
        val src = File("src/main/kotlin/com/music/echo/utils/OpenLinkServices.kt").readText()
        assertTrue(src.contains("setComponentEnabledSetting("))
        assertTrue(src.contains("getComponentEnabledSetting("))
        assertTrue("off must be explicit, not back to the manifest default", src.contains("COMPONENT_ENABLED_STATE_DISABLED"))
    }
}
