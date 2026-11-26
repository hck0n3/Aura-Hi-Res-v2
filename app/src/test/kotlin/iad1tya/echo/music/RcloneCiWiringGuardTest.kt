package iad1tya.echo.music

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 886-887 (plan phase 5.1a): the private cloud's rclone executable is never committed; every CI job
 * that assembles the app builds it first (scripts/build-rclone-android.sh), or the APK it produces silently ships
 * a private cloud that only says "not available". A new workflow or job that assembles the app without that step
 * fails here instead of in the owner's hands.
 */
class RcloneCiWiringGuardTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private val workflows: Map<String, String> by lazy {
        val dir = File(repoRoot, ".github/workflows")
        assertTrue(".github/workflows se movió", dir.isDirectory)
        dir.listFiles { f -> f.name.endsWith(".yml") || f.name.endsWith(".yaml") }!!
            .associate { it.name to it.readText() }
    }

    /**
     * Jobs that assemble the app but whose APK is never used, so the executable would only cost minutes:
     * CodeQL's traced compile is analysed and thrown away, and write-locks only commits gradle.lockfile files.
     */
    private val exempt = setOf("codeql.yml/analyze", "test-build.yml/write-locks")

    /** Top-level jobs of a workflow, as `name -> text`, comment lines removed. */
    private fun jobs(text: String): Map<String, String> {
        val body = text.substringAfter("\njobs:\n", "")
        val starts = Regex("^  ([A-Za-z0-9_-]+):[ \\t]*$", RegexOption.MULTILINE).findAll(body).toList()
        return starts.mapIndexed { i, m ->
            val end = if (i + 1 < starts.size) starts[i + 1].range.first else body.length
            m.groupValues[1] to body.substring(m.range.first, end)
                .lines().filterNot { it.trimStart().startsWith("#") }.joinToString("\n")
        }.toMap()
    }

    private val assembleApp = Regex("""\./gradlew\b[^\n]*?(?<![:\w])(?::app:)?assemble[A-Z]\w*""")

    private fun firstAppAssemble(job: String): Int? =
        job.lines().indexOfFirst { line -> assembleApp.containsMatchIn(line) && !line.contains(":smoketest:") }
            .takeIf { it >= 0 }

    @Test
    fun `every job that assembles the app builds the rclone executable first`() {
        val checked = mutableListOf<String>()
        for ((file, text) in workflows) {
            for ((name, job) in jobs(text)) {
                val id = "$file/$name"
                val assembleLine = firstAppAssemble(job) ?: continue
                if (id in exempt) continue
                checked += id
                val lines = job.lines()
                val buildLine = lines.indexOfFirst { it.contains("scripts/build-rclone-android.sh") && !it.contains("--stamp") }
                assertTrue("$id ensambla la app sin compilar rclone (scripts/build-rclone-android.sh)", buildLine >= 0)
                assertTrue("$id: rclone debe compilarse ANTES del primer assemble", buildLine < assembleLine)
                assertTrue("$id: falta la caché de tools/rclone-android/out", job.contains("path: tools/rclone-android/out"))
                assertTrue(
                    "$id: la clave de caché debe salir de la huella del script (--stamp)",
                    job.contains("scripts/build-rclone-android.sh --stamp") &&
                        Regex("""key: rclone-android-[\w-]+-\$\{\{ steps\.rclone\.outputs\.stamp }}""").containsMatchIn(job),
                )
                assertTrue("$id: falta Go para compilar rclone", job.contains("go-version-file: tools/rclone-android/go.mod"))
                assertTrue(
                    "$id: debe comprobar que el APK lleva librclone.so",
                    job.contains("scripts/rclone_android/rclone_android.py apk"),
                )
            }
        }
        // The three that ship or test APKs today; if one stops assembling, this list must be revisited by hand.
        assertEquals(
            listOf("emulator-smoke.yml/emulator-smoke", "gradle.yml/build", "test-build.yml/test-build"),
            checked.sorted(),
        )
    }

    @Test
    fun `every exempt job still exists and still assembles`() {
        for (id in exempt) {
            val (file, name) = id.split("/")
            val job = jobs(workflows[file] ?: error("$file desapareció: quita la excepción"))[name]
                ?: error("$id desapareció: quita la excepción")
            assertTrue("$id ya no ensambla: quita la excepción", firstAppAssemble(job) != null)
        }
    }

    @Test
    fun `an x86_64-only cache never shares a key with the every-ABI one`() {
        val smoke = workflows.getValue("emulator-smoke.yml")
        assertTrue(smoke.contains("scripts/build-rclone-android.sh x86_64"))
        assertTrue(smoke.contains("key: rclone-android-x86_64-"))
        for (file in listOf("gradle.yml", "test-build.yml")) {
            val text = workflows.getValue(file)
            assertTrue(file, text.contains("key: rclone-android-all-"))
            assertFalse(file, text.contains("scripts/build-rclone-android.sh x86_64"))
        }
    }

    @Test
    fun `the release checks both APKs carry the executable`() {
        val release = workflows.getValue("gradle.yml")
        assertTrue(release.contains("""rclone_android.py apk "${'$'}U" arm64-v8a armeabi-v7a x86_64 x86"""))
        assertTrue(release.contains("""rclone_android.py apk "${'$'}A" arm64-v8a"""))
    }

    @Test
    fun `the unit tests never need the binary`() {
        val unit = workflows.getValue("unit-tests.yml")
        assertFalse(unit.contains("build-rclone-android.sh"))
        assertTrue(unit.contains("python3 -m unittest test_rclone_android"))
    }

    @Test
    fun `the app packages the built executable untouched and never commits it`() {
        val gradle = File(repoRoot, "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("""jniLibs.directories.add(rootProject.file("tools/rclone-android/out/jniLibs").path)"""))
        assertTrue(gradle.contains(""""**/librclone.so""""))
        assertTrue(File(repoRoot, ".gitignore").readLines().contains("tools/rclone-android/out/"))
    }
}
