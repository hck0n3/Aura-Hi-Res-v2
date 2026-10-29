package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 519 — owner 2026-10-29: «cuando toque me gusta tiene que descargar la canción automáticamente… y si ya se
 * descargó en la caché que indique que ya está descargada y que no la tenga que descargar dos veces». The log showed the
 * automatic download dropped on mobile data for good («auto-download skipped on metered network»).
 */
class LikeDownloadGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").readText()

    @Test
    fun `every like path reaches the download decision, which adopts the listen copy before the metered rule`() {
        val util = main("playback/DownloadUtil.kt")
        val fn = util.substring(util.indexOf("fun downloadOnLike("), util.indexOf("fun flushPendingLikeDownloads()"))
        assertTrue(fn.indexOf("ListenCacheAdoption.adopt(") in 1 until fn.indexOf("if (!adopted && metered)"))
        assertTrue("sin copia y con datos: espera a Wi-Fi, no se pierde", fn.contains("PendingLikeDownloads.add("))
        assertTrue("el segundo aviso del mismo toque se ignora", fn.contains("LIKE_DEDUPE_MS"))
        assertTrue("el registro no lleva títulos", !fn.contains("Tag(\"DOWNLOAD\").i(\"LIKE_DOWNLOAD", ignoreCase = false) || !fn.contains("title)"))
        assertTrue(main("utils/SyncUtils.kt").contains("downloadUtil.get().downloadOnLike(s.id, s.title, s.isVideo)"))
        val svc = main("playback/MusicService.kt")
        assertTrue(svc.contains("downloadUtil.downloadOnLike("))
        assertTrue(svc.contains("downloadUtil.flushPendingLikeDownloads()"))
    }
}
