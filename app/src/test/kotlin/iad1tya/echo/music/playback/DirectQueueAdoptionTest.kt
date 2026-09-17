package iad1tya.echo.music.playback

import iad1tya.echo.music.playback.queues.EmptyQueue
import iad1tya.echo.music.playback.queues.ListQueue
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quien reemplaza la cola del reproductor tiene que decir de qué cola se trata.
 *
 * 🔴 REPORTE DEL DUEÑO (2026-09-17, Android Auto): *"me meto a un playlist o álbum y empiezo a
 * reproducir, y de la nada después de la canción que selecciono me pone otra cosa […] y para cuando
 * termine deberá seguir con la cola infinita. Verifica si el fallo está en toda la app"*.
 *
 * ## Qué protege esto
 * `MusicService.currentQueue` no es informativo, MANDA sobre dos cosas:
 *
 *  · a cinco canciones del final, `onMediaItemTransition` hace `currentQueue.nextPage()` y lo **añade**;
 *  · el traspaso a la radio infinita exige `!currentQueue.hasNextPage()`.
 *
 * Así que un sitio que reemplaza la línea de tiempo y NO actualiza `currentQueue` deja mandando a la
 * cola anterior: el servicio pagina una colección que ya no suena dentro de la que sí suena, y al
 * terminar nunca entrega el mando a la radio. Eran **tres** sitios (Android Auto y cualquier otro
 * controlador externo, `playNext` con el reproductor vacío, y el invitado de Listen Together).
 *
 * ## Por qué hace falta un test y no basta con haberlo arreglado
 * El fallo es **mudo**: no hay excepción, no hay log, la música no para. Simplemente suena algo que no
 * era. Y encima es intermitente — `EmptyQueue.hasNextPage()` es false, así que en un proceso recién
 * arrancado no pasa nada; hace falta haber usado la app antes. Así se estuvo escapando.
 */
class DirectQueueAdoptionTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    // ------------------------------------------------ la propiedad sobre la que se apoya el arreglo

    @Test
    fun `una lista finita no pagina`() {
        // Esto es LO que apaga la paginación ajena y enciende el traspaso a la radio al final. Si
        // ListQueue empezara a paginar, el arreglo entero dejaría de funcionar sin que nada fallara.
        assertFalse(
            "ListQueue.hasNextPage() dejó de ser false: las colas adoptadas por adoptDirectQueue " +
                "volverían a paginar y el traspaso a la radio infinita dejaría de dispararse",
            ListQueue(items = emptyList()).hasNextPage(),
        )
        assertFalse("EmptyQueue tampoco debe paginar", EmptyQueue.hasNextPage())
    }

    // ------------------------------------------------ que ningún sitio nuevo vuelva a abrir el agujero

    /**
     * Sitios que reemplazan la línea de tiempo y están exentos, con su motivo escrito:
     *  · `playQueue` es el camino normal y asigna `currentQueue` él mismo (por eso existe todo lo demás);
     *  · el reproductor SECUNDARIO del crossfade (`sec`) no es la línea de tiempo en vivo — es una copia
     *    que se prepara aparte y se intercambia; no tiene cola propia que adoptar.
     */
    @Test
    fun `todo sitio que reemplaza la cola la adopta`() {
        val offenders = mutableListOf<String>()
        var checked = 0

        File(repoRoot, "app/src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { i, line ->
                    if (!line.contains(".setMediaItems(")) return@forEachIndexed
                    if (Regex("""^\s*(//|\*)""").containsMatchIn(line)) return@forEachIndexed

                    val receiver = line.substringBefore(".setMediaItems(").trim().substringAfterLast(' ')
                    if (receiver == "sec") return@forEachIndexed // reproductor secundario del crossfade

                    val enclosing = (i downTo 0)
                        .firstNotNullOfOrNull { j -> Regex("""\bfun\s+(\w+)\s*\(""").find(lines[j])?.groupValues?.get(1) }
                        .orEmpty()
                    if (enclosing == "playQueue") return@forEachIndexed

                    checked++
                    val window = lines.subList(maxOf(0, i - 12), i).joinToString("\n")
                    if (!window.contains("adoptDirectQueue(")) {
                        offenders += "${file.name}:${i + 1}  (dentro de $enclosing)"
                    }
                }
            }

        assertTrue(
            "este test dejó de mirar nada: ya no se encuentra ningún setMediaItems fuera de playQueue, " +
                "así que dejó de proteger algo — revisa el patrón",
            checked >= 2,
        )
        assertTrue(
            "estos sitios reemplazan la cola del reproductor sin adoptarla, así que la cola ANTERIOR " +
                "sigue mandando: se le paginará encima otra colección y no habrá traspaso a la radio " +
                "infinita al terminar. Llama a adoptDirectQueue justo antes:\n  " +
                offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `la cola que llega de un controlador externo se adopta`() {
        // Este camino no llama a setMediaItems: media3 coloca los items por su cuenta a partir de lo que
        // devuelve onSetMediaItems, así que el escaneo de arriba no lo ve. Su adopción ocurre en
        // adoptExternalQueue, y es justo el caso que reportó el dueño (Android Auto).
        val service = File(repoRoot, "app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        assertTrue("MusicService.kt se movió — actualiza este test", service.isFile)
        val body = Regex("""fun adoptExternalQueue\((.*?)\n {4}\}""", RegexOption.DOT_MATCHES_ALL)
            .find(service.readText())
            ?.groupValues
            ?.get(1)
            ?: error("no se encontró adoptExternalQueue en MusicService.kt")

        assertTrue(
            "adoptExternalQueue dejó de adoptar la cola: Android Auto (y cualquier otro controlador " +
                "externo) volvería a heredar la paginación de la última cola de la app",
            body.contains("adoptDirectQueue("),
        )

        val callback = File(
            repoRoot,
            "app/src/main/kotlin/com/music/echo/playback/MediaLibrarySessionCallback.kt",
        )
        assertTrue(
            "onSetMediaItems dejó de pasarle los items resueltos: adoptExternalQueue adoptaría una " +
                "lista VACÍA, que no pagina pero tampoco es la cola que suena",
            callback.readText().contains("items = resolved.mediaItems"),
        )
    }
}
