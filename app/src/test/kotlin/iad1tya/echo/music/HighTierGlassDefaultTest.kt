package iad1tya.echo.music

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cristal interactivo encendido de fábrica en gama alta.
 *
 * 🔴 ORDEN DEL DUEÑO (2026-09-17): *"en los dispositivos que la app detecte que son de gama alta y
 * puedan mover bien el liquid glass y la función de cristal interactivo, que lo active por defecto para
 * que tenga la mejor experiencia visual"*.
 *
 * Las cuatro formas de romper esto **no dan ningún error**: la app arranca igual, no falla nada, y el
 * ajuste simplemente no se enciende. Por eso van fijadas por fuente.
 *
 *  1. **La puerta.** `batchAPending` es una cadena de `||`: si ninguna clave falta, el bloque
 *     `dataStore.edit { … }` NO se ejecuta y **ninguna** migración del lote corre. Una clave nueva que
 *     no esté en esa cadena nunca se aplica a quien ya tiene todas las demás — es decir, a **todos los
 *     que actualizan**, que son justamente para quienes se escribió.
 *  2. **Reusar una clave vieja.** Un flag ya marcado no vuelve a correr jamás, y
 *     `LiquidGlassHighTierV1AppliedKey` está marcada en todo el que actualizó desde 0.6.127. Esta
 *     lección ya está escrita tres veces en `PreferenceKeys.kt`; este test la convierte en un fallo.
 *  3. **Escribir solo una de las dos claves.** La fila del cristal interactivo está deshabilitada
 *     mientras el maestro esté apagado: escribir solo el interactivo deja un ajuste que promete y no
 *     dibuja.
 *  4. **Dos definiciones distintas de "gama alta".** Acabarían encendiendo una cosa y no la otra.
 */
class HighTierGlassDefaultTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private val app: String by lazy {
        val f = File(repoRoot, "app/src/main/kotlin/com/music/echo/App.kt")
        assertTrue("App.kt se movió — actualiza este test para seguirlo", f.isFile)
        f.readText()
    }

    private val flag = "LiquidGlassInteractiveHighTierV1AppliedKey"

    /** El cuerpo del `if (settings[…flag] != true) { … }` que aplica la orden. */
    private val migration: String by lazy {
        Regex(
            """if \(settings\[[\w.]*$flag\] != true\) \{(.*?)\n {8}\}""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(app)?.groupValues?.get(1)
            ?: error("no se encontró la migración de $flag en App.kt")
    }

    @Test
    fun `la clave esta en la puerta que decide si se siembra`() {
        val gate = Regex("""val batchAPending =(.*?)\n {8}if \(batchAPending\)""", RegexOption.DOT_MATCHES_ALL)
            .find(app)?.groupValues?.get(1)
            ?: error("no se encontró batchAPending en App.kt")
        assertTrue(
            "$flag no está en la cadena de batchAPending. Sin eso, en cuanto el resto de flags ya " +
                "estén puestos —o sea, en TODOS los que actualizan— el bloque de siembra no corre y " +
                "esta orden no se aplica a nadie. Y no da ningún error.",
            gate.contains(flag),
        )
    }

    @Test
    fun `usa una clave fresca y no la de 0 6 127`() {
        assertTrue("la migración tiene que existir", migration.isNotBlank())
        assertFalse(
            "la migración del cristal interactivo cuelga de LiquidGlassHighTierV1AppliedKey, que ya " +
                "está marcada en todo el que actualizó desde 0.6.127: no volvería a correr nunca",
            migration.contains("LiquidGlassHighTierV1AppliedKey"),
        )
    }

    @Test
    fun `enciende el maestro Y el interactivo`() {
        assertTrue(
            "falta encender el maestro: la fila del cristal interactivo está deshabilitada sin él, " +
                "así que quedaría un ajuste activado que no dibuja nada",
            migration.contains("LiquidGlassGlobalEnabledKey] = true"),
        )
        assertTrue(
            "falta encender el cristal interactivo, que es lo que pidió el dueño",
            migration.contains("LiquidGlassInteractiveKey] = true"),
        )
    }

    @Test
    fun `las dos migraciones de gama alta usan el MISMO test de capacidad`() {
        // Reutilizado literalmente, no reescrito: dos criterios distintos para "gama alta" dejarían el
        // maestro encendido y el interactivo apagado, o al revés, según el teléfono.
        val tierTest = "PerformanceMode.effectiveTier(this@App) ==\n" +
            "                    iad1tya.echo.music.utils.DeviceTier.HIGH"
        assertTrue(
            "la migración ya no comprueba el tier HIGH con isGlassEligible + effectiveTier, que es el " +
                "mismo test que usa la migración de 0.6.127",
            migration.contains("isGlassEligible(this@App)") && migration.contains(tierTest),
        )
        assertTrue(
            "ese test tiene que aparecer DOS veces en App.kt — una por migración de gama alta. Si " +
                "solo aparece una, alguna de las dos cambió de criterio",
            Regex(Regex.escape(tierTest)).findAll(app).count() == 2,
        )
    }

    @Test
    fun `el flag se marca tambien cuando el telefono no es de gama alta`() {
        // Si solo se marcara dentro del `if (glassHigh)`, cada arranque de un teléfono de gama media
        // volvería a evaluar la capacidad y a escribir en DataStore para nada. Marcarlo no cierra
        // ninguna puerta: el interruptor sigue en Ajustes y lo que el usuario elija manda desde ahí.
        val write = "$flag] = true"
        assertTrue("la migración tiene que marcar su propio flag", migration.contains(write))

        val insideGlassHigh = Regex("""if \(glassHigh\) \{(.*?)\n {12}\}""", RegexOption.DOT_MATCHES_ALL)
            .find(migration)?.groupValues?.get(1)
            ?: error("no se encontró el bloque if (glassHigh) dentro de la migración")
        assertFalse(
            "el flag se marca solo cuando el teléfono es de gama alta: los demás re-evaluarían la " +
                "capacidad del dispositivo y escribirían en DataStore en CADA arranque",
            insideGlassHigh.contains(write),
        )
    }
}
