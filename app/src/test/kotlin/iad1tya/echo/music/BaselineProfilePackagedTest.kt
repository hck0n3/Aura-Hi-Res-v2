package iad1tya.echo.music

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El baseline profile solo sirve si algo lo instala.
 *
 * 🔴 CONTEXTO (2026-09-16): el dueño pidió que Aura fuera *"igual de fluida o mejor"* que SimpMusic, y el
 * perfil (`app/src/main/baselineProfiles/baseline-prof.txt`) es la pieza que faltaba. Pero un perfil
 * empaquetado **no se aplica solo** en un APK repartido fuera de Play: Play lo aplica al instalar, y Aura
 * se reparte desde GitHub. Quien lo escribe en el directorio de perfiles de ART en el primer arranque es
 * `androidx.profileinstaller`, a través de su `InitializationProvider`.
 *
 * Esa librería **ya** está en el classpath de runtime de este módulo, de forma transitiva — no hace falta
 * declararla, y declararla rompió la compilación: la metía también en el classpath de COMPILACIÓN, donde
 * el lock no la lista ("not part of the dependency lock state"), y este contenedor no puede regenerar el
 * lock porque el proxy bloquea Maven.
 *
 * Depender de una transitiva es cómodo hasta el día que desaparece. Y este fallo es **silencioso**: el
 * perfil seguiría viajando dentro del APK, nadie vería un error, y simplemente dejaría de aplicarse. Este
 * test es lo que convierte ese silencio en un fallo de CI.
 */
class BaselineProfilePackagedTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    @Test
    fun `the profile itself is there and has rules`() {
        val profile = File(repoRoot, "app/src/main/baselineProfiles/baseline-prof.txt")
        assertTrue(
            "baseline-prof.txt is gone. AGP reads it from this exact path; moving it silently ships an " +
                "APK with no profile at all.",
            profile.isFile,
        )
        val rules = profile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        assertTrue("the profile has no rules left in it — it would do nothing", rules.size >= 5)
        assertTrue(
            "every rule must name this app's own classes (Liad1tya/echo/music/...). A rule for another " +
                "package is either a typo or a library's profile copied in, which AGP already merges.",
            rules.all { it.contains("iad1tya/echo/music") },
        )
    }

    @Test
    fun `profileinstaller is still on the runtime classpath`() {
        val lock = File(repoRoot, "app/gradle.lockfile")
        assertTrue("app/gradle.lockfile has moved — update this test to follow it", lock.isFile)
        val line = lock.readLines()
            .firstOrNull { it.startsWith("androidx.profileinstaller:profileinstaller:") }

        assertTrue(
            "androidx.profileinstaller is no longer in the lock at all. Nothing installs the baseline " +
                "profile on a sideloaded APK without it, so the profile would ship and never apply — " +
                "with no error anywhere. Add it back (explicitly if the transitive is really gone), and " +
                "remember an explicit declaration ALSO needs the compile classpaths regenerated in the " +
                "lock with ./gradlew dependencies --write-locks.",
            line != null,
        )

        val configurations = line!!.substringAfter('=').split(',')
        // Runtime is what matters: that is what puts the InitializationProvider in the merged manifest.
        // Checked for BOTH release and debug, because the release APK is the one users install and the
        // debug one is the beta the owner tests.
        listOf("universalFossReleaseRuntimeClasspath", "universalFossDebugRuntimeClasspath").forEach { config ->
            assertTrue(
                "profileinstaller is in the lock but not on $config, so it would not ship in that " +
                    "variant and its baseline profile would never be installed.",
                configurations.any { it.trim() == config },
            )
        }
    }
}
