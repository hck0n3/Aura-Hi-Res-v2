package iad1tya.echo.music

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

/** Rows 865-866 (HALLAZGO-010): the only artifacts that needed the aliyun mirror are gone, and so is the mirror. */
class AliyunMirrorRemovedTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    @Test
    fun `no build file resolves from the aliyun mirror`() {
        for (path in listOf("settings.gradle.kts", "build.gradle.kts")) {
            assertFalse(path, File(root, path).readText().contains("maven.aliyun.com"))
        }
    }
}
