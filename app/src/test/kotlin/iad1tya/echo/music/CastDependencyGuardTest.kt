package iad1tya.echo.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Row 863 (owner decision H18): Cast framework 23.x and MediaRouter 1.9.x, gms flavour only.
 *
 * - play-services-cast-framework 23.0.0 itself depends on mediarouter 1.9.0-beta01, so mediarouter must stay
 *   declared at a STABLE version >= 1.9.0; otherwise Gradle silently resolves the beta for the gms build.
 * - The foss flavour must never carry a Google Play Services artifact (nor MediaRouter / media3-cast).
 */
class CastDependencyGuardTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private val toml by lazy { File(repoRoot, "gradle/libs.versions.toml").readText() }

    private fun declared(key: String): String =
        Regex("""(?m)^$key = "([^"]+)"""").find(toml)?.groupValues?.get(1)
            ?: error("$key not declared in gradle/libs.versions.toml")

    private val stable = Regex("""^\d+\.\d+\.\d+$""")

    private fun parts(v: String) = v.split('.').map { it.toInt() }

    private fun atLeast(v: String, min: String): Boolean {
        val a = parts(v)
        val b = parts(min)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return true
    }

    @Test
    fun castFrameworkIsAStable23Release() {
        val v = declared("castFramework")
        assertTrue("castFramework = $v must be a stable release", stable.matches(v))
        assertTrue("castFramework = $v must be on the 23.x line (row 863)", v.startsWith("23."))
    }

    @Test
    fun mediaRouterIsAStableReleaseAtLeastTheOneCast23Needs() {
        val v = declared("mediarouter")
        assertTrue("mediarouter = $v must be a stable release", stable.matches(v))
        assertTrue(
            "mediarouter = $v is below 1.9.0: Cast 23 would drag in mediarouter 1.9.0-beta01 (row 863)",
            atLeast(v, "1.9.0"),
        )
    }

    @Test
    fun castLibrariesAreDeclaredForTheGmsFlavourOnly() {
        val lines = File(repoRoot, "app/build.gradle.kts").readLines()
        listOf("libs.media3.cast)", "libs.mediarouter)", "libs.cast.framework)").forEach { accessor ->
            val uses = lines.map { it.trim() }.filter { !it.startsWith("//") && it.contains(accessor) }
            assertTrue("$accessor is not declared in app/build.gradle.kts", uses.isNotEmpty())
            uses.forEach { line ->
                assertTrue(
                    "$accessor must only be a gmsImplementation dependency (foss stays GMS-free): $line",
                    line.startsWith("\"gmsImplementation\"("),
                )
            }
        }
    }

    @Test
    fun fossClasspathsLockNoGmsCastArtifact() {
        val lock = File(repoRoot, "app/gradle.lockfile").readLines()
        val gmsOnly = listOf("com.google.android.gms:", "androidx.mediarouter:", "androidx.media3:media3-cast:")
        lock.filter { line -> gmsOnly.any { line.startsWith(it) } }.forEach { line ->
            val configurations = line.substringAfter('=').split(',')
            val foss = configurations.filter { it.contains("Foss") }
            assertTrue("${line.substringBefore('=')} reaches foss classpaths: $foss", foss.isEmpty())
        }
    }

    @Test
    fun gmsReleaseLocksTheDeclaredCastAndMediaRouterVersions() {
        val lock = File(repoRoot, "app/gradle.lockfile").readLines()
        mapOf(
            "com.google.android.gms:play-services-cast-framework:" to declared("castFramework"),
            "androidx.mediarouter:mediarouter:" to declared("mediarouter"),
        ).forEach { (artifact, want) ->
            val locked = lock.filter { it.startsWith(artifact) && it.contains("GmsReleaseRuntimeClasspath") }
                .map { it.removePrefix(artifact).substringBefore('=') }
            assertTrue("$artifact is not locked for the gms release", locked.isNotEmpty())
            locked.forEach { assertEquals("$artifact locked version (regenerate the lockfiles)", want, it) }
        }
    }
}
