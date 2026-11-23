package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 773: the Aura song swipe threshold in dp, the trending loading toast, the Safe volume / glue
 * compressor / limiter texts as resources, and the Sencillo/EP/Álbum label on the classic radar rows.
 */
class TextsAndSwipeThresholdGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun kt(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private fun res(path: String) =
        File(root, "app/src/main/res/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the Aura song swipe threshold is a dp distance, not raw pixels`() {
        val box = kt("ui/newui/AuraContent.kt").substringAfter("fun AuraSwipeSongBox(").substringBefore("private const val AURA_LIKE_THRESHOLD")
        assertTrue(box.contains("val threshold = with(LocalDensity.current) { 100.dp.toPx() }"))
        assertFalse(box.contains("val threshold = 300f"))
        // Direction meanings unchanged: right = play next, left = add to queue.
        assertTrue(box.contains("offset.floatValue >= threshold -> {") && box.contains("player?.playNext(listOf(mediaItem))"))
        assertTrue(box.contains("offset.floatValue <= -threshold -> {") && box.contains("player?.addToQueue(listOf(mediaItem))"))
    }

    @Test
    fun `the trending loading toasts use one string resource in both languages`() {
        val tabs = kt("ui/newui/AuraSearchTabs.kt")
        assertFalse(tabs.contains("\"Cargando \${"))
        assertEquals(4, Regex("""context\.getString\(R\.string\.aura_trending_loading_item, """).findAll(tabs).count())
        assertTrue(res("values/aura_search_strings.xml").contains("<string name=\"aura_trending_loading_item\">Loading %1\$s...</string>"))
        assertTrue(res("values-es/aura_search_strings.xml").contains("<string name=\"aura_trending_loading_item\">Cargando %1\$s...</string>"))
    }

    @Test
    fun `sound texts are resources with the same Spanish wording`() {
        val sound = kt("ui/screens/settings/SoundSettings.kt")
        for (key in listOf("sound_safe_volume_title", "sound_safe_volume_desc", "sound_glue_compressor_title", "sound_glue_compressor_desc")) {
            assertTrue(key, sound.contains("Text(stringResource(R.string.$key))"))
        }
        assertFalse(sound.contains("Text(\"Volumen seguro\")"))
        assertFalse(sound.contains("Text(\"Compresor suave (pegamento)\")"))
        val welcome = kt("ui/screens/WelcomeDialog.kt")
        assertTrue(welcome.contains("val soundLimiterBullet = stringResource(R.string.welcome_sound_limiter_bullet)"))
        assertTrue(welcome.contains("val pages = remember(soundLimiterBullet) {"))
        assertFalse(welcome.contains("\"Limitador y margen automático opcional"))

        val es = res("values-es/sound_texts_strings.xml")
        assertTrue(es.contains("<string name=\"sound_safe_volume_title\">Volumen seguro</string>"))
        assertTrue(
            es.contains(
                "<string name=\"sound_safe_volume_desc\">Iguala el volumen entre temas: baja los muy fuertes y sube los silenciosos (hasta +12 dB), con un limitador suave. Activado por defecto.</string>",
            ),
        )
        assertTrue(es.contains("<string name=\"sound_glue_compressor_title\">Compresor suave (pegamento)</string>"))
        assertTrue(
            es.contains(
                "<string name=\"sound_glue_compressor_desc\">Compresión 2:1 por encima de −12 dB, sin ganancia de compensación: baja un poco los pasajes fuertes para unir la mezcla. Apagado por defecto.</string>",
            ),
        )
        assertTrue(es.contains("<string name=\"welcome_sound_limiter_bullet\">Limitador y margen automático opcional; sin «mejoras» inventadas sobre Opus</string>"))
        val en = res("values/sound_texts_strings.xml")
        for (key in listOf("sound_safe_volume_title", "sound_safe_volume_desc", "sound_glue_compressor_title", "sound_glue_compressor_desc", "welcome_sound_limiter_bullet")) {
            assertTrue("$key en values/", en.contains("<string name=\"$key\">"))
        }
    }

    @Test
    fun `the classic radar row says Sencillo, EP or Album with the Novedades label`() {
        val radar = kt("ui/screens/library/ReleaseRadarScreen.kt")
        assertTrue(radar.contains("val typeLabel = novedadesTypeLabel("))
        assertTrue(radar.contains("description = NovedadesShelves.typeHint(item.type),"))
        assertTrue(radar.contains("text = listOfNotNull(typeLabel, item.artist, item.releaseDate.year.toString()).joinToString(\" · \"),"))
        // Same function the Novedades cards use (rows 653/710).
        val novedades = kt("ui/newui/AuraNovedadesScreen.kt")
        assertTrue(novedades.contains("internal fun novedadesTypeLabel(kind: AuraContentKind): String? = when (kind) {"))
        assertTrue(novedades.contains("description = NovedadesShelves.typeHint(r.type)"))
    }
}
