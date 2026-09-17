package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cada sección de una lista perezosa tiene que decir DE QUÉ TIPO es.
 *
 * 🔴 ORDEN DEL DUEÑO (2026-09-16): *"verifica la fluidez máxima de la animación y en el
 * desplazamiento de scroleo, quiero la máxima fluidez, lo más optimizado posible"*.
 *
 * ## Qué hace `contentType` y por qué su ausencia cuesta frames
 * Compose no tira los slots de los items que salen de pantalla: los guarda en una piscina para
 * reutilizarlos cuando entra otro. Para decidir si un slot sirve llama a `areCompatible`, que
 * compara **contentType**.
 *
 * Con `contentType = null` en todos, cualquier slot es compatible con cualquier item. O sea que el
 * slot que tenía un carrusel de 300 dp se le entrega a la siguiente sección, que no comparte ni un
 * nodo con él: Compose desactiva el árbol viejo, intenta reaprovecharlo, no puede, y compone el
 * nuevo igual. Se paga la desactivación a cambio de nada, y se paga **en cada frontera de sección**
 * — que en Inicio, con ~25 formas distintas en una sola `LazyColumn`, es todo el rato que estás
 * haciendo scroll.
 *
 * Con el tipo puesto, Compose ve que no hay slot compatible y compone uno nuevo directamente. Menos
 * trabajo por frame, sin cambiar un solo píxel de lo que se dibuja.
 *
 * ## Por qué hace falta un test y no basta con haberlo arreglado
 * `contentType` es una **pista**, no un requisito: olvidarlo no da error, no rompe el layout y no se
 * ve en una captura. Simplemente vuelve a costar frames. La apariencia clásica llevaba 58 usos y la
 * nueva tenía 1 — nadie lo notó hasta medirlo. Sin este test, la sección número 165 lo pierde otra
 * vez y nadie se entera.
 *
 * ## El detalle que decide si sirve de algo: la FORMA, no la instancia
 * Una clave interpolada (`"aura_mood_section_$index"`) identifica una instancia concreta, pero N
 * secciones de ese molde comparten estructura y DEBEN compartir tipo — si cada una llevara el suyo,
 * la piscina no podría reutilizar ninguna y el arreglo se volvería en contra. Por eso el tipo es la
 * clave con la interpolación quitada.
 */
class LazyContentTypeTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private val newUiDir: File by lazy {
        val d = File(repoRoot, "app/src/main/kotlin/com/music/echo/ui/newui")
        assertTrue("la carpeta de la interfaz nueva se movió — actualiza este test", d.isDirectory)
        d
    }

    /** `item(key = "algo")` — las secciones sueltas, que son las heterogéneas entre sí. */
    private val singletonItem = Regex("""\bitem\(\s*key\s*=\s*"([^"]*)"([^)\n]*)""")

    @Test
    fun `toda seccion suelta de la interfaz nueva declara su contentType`() {
        val offenders = mutableListOf<String>()
        var checked = 0

        newUiDir.listFiles { f -> f.name.endsWith(".kt") }.orEmpty().sortedBy { it.name }.forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                val m = singletonItem.find(line) ?: return@forEachIndexed
                checked++
                if (!m.groupValues[2].contains("contentType")) {
                    offenders += "${file.name}:${i + 1}  key=\"${m.groupValues[1]}\""
                }
            }
        }

        assertTrue(
            "este test no está mirando nada — el patrón `item(key = \"…\")` dejó de encontrarse en " +
                "la interfaz nueva, así que dejó de proteger algo",
            checked >= 100,
        )
        assertTrue(
            "estas secciones de lista perezosa no declaran contentType, así que Compose le entregará " +
                "su slot a una sección de otra forma y pagará una desactivación inútil en cada " +
                "frontera mientras se hace scroll:\n  " + offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `un contentType nunca lleva interpolacion dentro`() {
        // La forma, no la instancia. Un tipo por índice deja la piscina sin nada que reutilizar
        // entre secciones del mismo molde, que es peor que no haber puesto nada.
        val interpolated = Regex("""contentType\s*=\s*"[^"]*\$""")
        val offenders = mutableListOf<String>()

        newUiDir.listFiles { f -> f.name.endsWith(".kt") }.orEmpty().sortedBy { it.name }.forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                if (interpolated.containsMatchIn(line)) offenders += "${file.name}:${i + 1}"
            }
        }

        assertTrue(
            "un contentType con interpolación crea un tipo por instancia: cada sección del mismo " +
                "molde queda sola en su categoría y la piscina no puede reutilizar ninguna\n  " +
                offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }
}
