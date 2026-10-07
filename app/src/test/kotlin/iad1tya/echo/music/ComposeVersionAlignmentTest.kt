package iad1tya.echo.music

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Row 340: 2.0.64-beta4 crashed when opening sheets/dialogs from the player ("Agregar a playlist",
 * "Pedir música") with a ClassCastException inside layout. Adaptive 1.4.0-alpha02 + Material Kolor 5
 * dragged Compose UI/Foundation to 1.12 while Material 3 stayed on 1.5.0-alpha18, built for Compose 1.11:
 * it compiles, then breaks at runtime. This pins the resolved Compose runtime family to the one declared
 * in gradle/libs.versions.toml, so a transitive bump fails here instead of on the owner's phone.
 */
class ComposeVersionAlignmentTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private fun majorMinor(v: String) = v.split('.').take(2).joinToString(".")

    @Test
    fun lockedComposeMatchesTheDeclaredFamily() {
        val toml = File(repoRoot, "gradle/libs.versions.toml").readText()
        val declared = Regex("""(?m)^compose = "([^"]+)"""").find(toml)!!.groupValues[1]
        val lock = File(repoRoot, "app/gradle.lockfile").readLines()
        listOf("androidx.compose.ui:ui:", "androidx.compose.foundation:foundation:", "androidx.compose.runtime:runtime:")
            .forEach { artifact ->
                val locked = lock.filter { it.startsWith(artifact) && it.contains("ReleaseRuntimeClasspath") }
                    .map { it.removePrefix(artifact).substringBefore('=') }
                assertTrue("$artifact not locked", locked.isNotEmpty())
                locked.forEach { v ->
                    assertTrue(
                        "$artifact is locked at $v but compose = $declared: a transitive bump " +
                            "(row 340) — Material 3 is built for ${majorMinor(declared)}",
                        majorMinor(v) == majorMinor(declared),
                    )
                }
            }
    }
}
