package iad1tya.echo.music.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Registry row 867 (plan phase 3, owner decision A1 and H27): the English and Spanish texts stop naming YouTube where the
 * name is only branding («tu cuenta de Google», «el catálogo en línea», «en línea», «de la nube»). A text may keep the name
 * only when it is functional — the user has to find, open or pick something called YouTube — and then it is listed below
 * with the reason. Extends row 815 (which only covered «YouTube Music»). Keys are never renamed, only texts.
 */
class YouTubeNameInStringsGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    /** values-es-rUS shadows values-es for the owner's es-419 (row 784), so it is checked too. */
    private val folders = listOf("values", "values-es", "values-es-rUS")

    private val serviceName = Regex("youtube|yt music", RegexOption.IGNORE_CASE)

    /** Keys allowed to name YouTube, each with WHY it is functional. */
    private val functional = mapOf(
        "string:open_links_youtube_title" to "row 676: the Abrir enlaces toggle for youtube.com / music.youtube.com links",
        "string:open_links_prompt_body" to "row 676: Android lists the supported links by domain; the user must pick the YouTube ones",
        "string:migrate_apple_intro" to "Apple's own transfer page names YouTube Music as the destination the user must pick",
        "string:listen_on_youtube_music" to "row 815: no R.string reference in the app (never shown); key kept, not renamed",
        "string:playback_error_age_restricted" to "the age check is done on youtube.com with the same account; the user must go there",
        "string:import_from_youtube" to "row 815: the user pastes a YouTube link",
        "string:import_youtube_playlist_title" to "row 815: the user pastes a YouTube link",
        "string:import_youtube_playlist_hint" to "row 815: the user pastes the link of a public YouTube playlist",
        "string:youtube_url_column" to "CSV import: the user picks the column that holds YouTube URLs",
        "string:login_manual_cookie_body" to "the user must copy the cookie from music.youtube.com in a browser",
    )

    /** Branding texts rewritten by row 867: the keys must stay (they are referenced by R.string and never renamed). */
    private val rewritten = listOf(
        "not_logged_in_youtube", "send_playback_metrics_description", "feedback_howto_body",
        "decryption_library_newpipe_desc", "device_sync_library_desc", "ytm_upload_incomplete_summary",
        "playback_error_not_available", "playback_error_access_rejected", "your_youtube_playlists",
        "delete_playlist_from_youtube_too", "delete_playlist_only_local_note", "delete_playlist_youtube_failed",
        "login_manual_cookie_title", "sub_cleanup_desc", "sub_cleanup_intro", "sub_cleanup_pending_badge",
        "sub_cleanup_confirm_message", "sub_cleanup_confirmed_badge", "sub_cleanup_resend_badge", "sub_cleanup_incomplete",
    )

    /** "tag:name" → its text (plurals and arrays: every item joined), from every XML file of one values folder. */
    private fun entries(dir: String): Map<String, String> {
        val folder = File(root, "app/src/main/res/$dir")
        assertTrue("$dir moved", folder.isDirectory)
        val out = LinkedHashMap<String, String>()
        val factory = DocumentBuilderFactory.newInstance()
        folder.listFiles { f -> f.name.endsWith(".xml") }!!.sortedBy { it.name }.forEach { file ->
            val children = factory.newDocumentBuilder().parse(file).documentElement.childNodes
            for (i in 0 until children.length) {
                val el = children.item(i) as? Element ?: continue
                val name = el.getAttribute("name").takeIf { it.isNotEmpty() } ?: continue
                out["${el.tagName}:$name"] = el.textContent.trim()
            }
        }
        return out
    }

    @Test
    fun `no en, es or es-rUS string names YouTube unless it is functional`() {
        folders.forEach { dir ->
            val offenders = entries(dir).filter { (key, text) -> serviceName.containsMatchIn(text) && key !in functional }
            assertTrue("$dir still names YouTube: ${offenders.keys.joinToString()}", offenders.isEmpty())
        }
    }

    @Test
    fun `every functional key still exists and still names the service`() {
        val en = entries("values")
        functional.keys.forEach { key ->
            assertTrue("$key: drop it from the functional list", en[key]?.let { serviceName.containsMatchIn(it) } == true)
        }
    }

    @Test
    fun `the rewritten keys are still defined in en and es`() {
        val en = entries("values")
        val es = entries("values-es")
        rewritten.forEach { name ->
            assertTrue("values lost $name", "string:$name" in en)
            assertTrue("values-es lost $name", "string:$name" in es)
        }
    }

    @Test
    fun `the main rewrites say Google account or online catalog`() {
        val es = entries("values-es")
        assertEquals("No has iniciado sesión con tu cuenta de Google", es["string:not_logged_in_youtube"])
        assertEquals("Eliminar también de tu cuenta de Google", es["string:delete_playlist_from_youtube_too"])
        assertEquals("Tus listas de la nube", es["string:your_youtube_playlists"])
        assertEquals("Pegar cookie", es["string:login_manual_cookie_title"])
        assertTrue(es["string:playback_error_not_available"]!!.startsWith("El catálogo en línea no ofrece esta canción"))
        val en = entries("values")
        assertEquals("You are not signed in with your Google account", en["string:not_logged_in_youtube"])
        assertEquals("Delete from your Google account too", en["string:delete_playlist_from_youtube_too"])
        assertEquals("Your cloud playlists", en["string:your_youtube_playlists"])
        assertEquals("Paste cookie", en["string:login_manual_cookie_title"])
    }
}
