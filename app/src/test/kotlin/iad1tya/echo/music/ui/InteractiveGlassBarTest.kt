package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La barra flotante sin texto del cristal interactivo.
 *
 * 🔴 ORDEN DEL DUEÑO (2026-09-16): *"en el mini reproductor con su barra de abajo que solo salgan
 * los iconos flotantes en una barra sin texto para que el efecto liquid glass cristal interactivo
 * se vea mejor, pero solo cuando esté activado"*.
 *
 * Lo que este test protege son las DOS mitades de la frase, y la segunda es la que se rompe sola:
 *
 * 1. **"solo cuando esté activado"** — todo cuelga de `interactiveGlass` / `glassConfig.interactive`.
 *    Si alguien suelta una de estas piezas del interruptor, la barra pierde el texto para TODO el
 *    mundo, incluida la gente que nunca encendió el cristal. Eso no da ningún error.
 *
 * 2. **La altura total no puede moverse.** MainActivity calcula `navSlideDistance` como
 *    `bottomInset + AuraNavBarHeight`, y de ahí salen el deslizamiento de la hoja del reproductor y
 *    el inset inferior del contenido. Lo flotante se consigue con márgenes DENTRO de esa altura. Si
 *    un día alguien lo "arregla" restándole altura a la barra, la última fila de cada lista queda
 *    tapada o flotando — y tampoco da ningún error.
 */
class InteractiveGlassBarTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private fun source(path: String): String {
        val f = File(repoRoot, path)
        assertTrue("$path se movió — actualiza este test para seguirlo", f.isFile)
        return f.readText()
    }

    private val shell by lazy { source("app/src/main/kotlin/com/music/echo/ui/newui/AuraShell.kt") }
    private val classicBar by lazy {
        source("app/src/main/kotlin/com/music/echo/ui/component/FloatingNavigationToolbar.kt")
    }

    @Test
    fun `la etiqueta de la barra nueva depende del interruptor y de nada mas`() {
        assertEquals(
            "las celdas de AuraNavigationBar tienen que recibir `showLabel = !interactiveGlass`. " +
                "Fijarlo a true o a false desengancha el texto del interruptor",
            2, // las pestañas + la celda de Ajustes
            Regex("""showLabel = !interactiveGlass""").findAll(shell).count(),
        )
        assertFalse(
            "apareció un showLabel constante: eso quita (o deja) el texto para todo el mundo, " +
                "encendido o apagado",
            Regex("""showLabel = (true|false)\b""").containsMatchIn(shell),
        )
    }

    @Test
    fun `la etiqueta del clasico depende del mismo interruptor`() {
        assertTrue(
            "FloatingNavigationToolbar volvió a fijar showSelectedLabels: la cápsula clásica " +
                "mostraría texto con el cristal interactivo encendido, al revés que la nueva",
            classicBar.contains("val showSelectedLabels = !glassConfig.interactive"),
        )
    }

    @Test
    fun `la altura de la barra no cambia al volverse flotante`() {
        // Lo flotante son MÁRGENES dentro de la misma caja que ya medía AuraNavBarHeight.
        assertTrue(
            "la caja de la barra dejó de medir AuraNavBarHeight — si la altura cambia con el " +
                "interruptor, navSlideDistance y el inset del contenido dejan de coincidir con ella",
            shell.contains(".height(AuraNavBarHeight)"),
        )
        assertFalse(
            "AuraNavBarHeight pasó a depender de algo: MainActivity lo lee como una constante para " +
                "calcular el deslizamiento y el inset, así que tiene que seguir siendo un valor fijo",
            Regex("""val AuraNavBarHeight: Dp\s*(get\(\)|=\s*if)""").containsMatchIn(shell),
        )
    }

    @Test
    fun `la capsula y el minirreproductor comparten margen lateral`() {
        // Se dibujan una encima de otra: dos números distintos no se leen como dos márgenes, se leen
        // como bordes desalineados.
        assertEquals(
            "el margen lateral flotante tiene que salir de AuraFloatingInset en los DOS sitios " +
                "(la píldora del mini y la cápsula de navegación), o se separan al primer ajuste",
            2,
            Regex("""padding\(horizontal = AuraFloatingInset""").findAll(shell).count(),
        )
    }

    @Test
    fun `la capsula recibe la forma de capsula, no una por defecto`() {
        // Lección de la fila 254: `drawBackdrop` coloca el borde de luz y la refracción SEGÚN la
        // forma que se le pasa. Con la forma equivocada el borde abraza una silueta que luego el
        // padre recorta — que es como salieron las esquinas cuadradas del minirreproductor.
        val capsule = Regex(
            """liquidGlassInteractive\(\s*config = glassConfig,\s*shape = AuraShapes\.Pill,\s*interactive = true,""",
        )
        assertTrue(
            "la cápsula de navegación tiene que pasarle AuraShapes.Pill a su cristal e ir con " +
                "interactive = true (flotando, el escalado al pulsar ya no se lee como un fallo " +
                "de dibujo, que era el motivo de tenerlo apagado)",
            capsule.containsMatchIn(shell),
        )
    }
}
