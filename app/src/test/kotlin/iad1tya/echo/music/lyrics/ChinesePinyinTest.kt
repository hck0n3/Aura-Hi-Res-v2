package iad1tya.echo.music.lyrics

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 866: Chinese lyrics romanization moved from tinypinyin to pinyin4j. Expected values are what
 * tinypinyin 2.0.3 produced (checked side by side over U+4E00..U+9FFF: same first reading for every
 * character but U+8FD9).
 */
class ChinesePinyinTest {

    private fun romanize(text: String) = runBlocking { LyricsUtils.romanizeChinese(text) }

    @Test
    fun `artist names romanize as before`() {
        assertEquals("zhou jie lun", romanize("周杰伦"))
        assertEquals("wang fei", romanize("王菲"))
        assertEquals("deng zi qi", romanize("邓紫棋"))
        assertEquals("lin jun jie", romanize("林俊杰"))
        assertEquals("chen yi xun", romanize("陈奕迅"))
    }

    @Test
    fun `u-umlaut is written as v and polyphones take the first reading`() {
        assertEquals("nv", romanize("女"))
        assertEquals("lv", romanize("绿"))
        assertEquals("zhong qing", romanize("重庆"))
    }

    @Test
    fun `punctuation and latin text are kept`() {
        assertEquals("ni hao，shi jie", romanize("你好，世界"))
        assertEquals("ni hao, world!", romanize("你好, world!"))
        assertEquals("I love ni", romanize("I love 你"))
        assertEquals("", romanize(""))
    }

    @Test
    fun `a character without a reading stays as it is`() {
        assertEquals("\u9fa6", romanize("\u9fa6")) // no pinyin4j reading
    }

    @Test
    fun `the one reading that changed is the standard one`() {
        // tinypinyin said "zhei" for 这; pinyin4j's first reading is the standard "zhe".
        assertEquals("zhe", romanize("这"))
    }

    @Test
    fun `tinypinyin is gone from the build`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        val catalog = File(root, "gradle/libs.versions.toml").readText()
        assertTrue(catalog.contains("pinyin4j = { module = \"com.belerweb:pinyin4j\", version.ref = \"pinyin4j\" }"))
        assertFalse(catalog.contains("tinypinyin"))
        assertTrue(File(root, "app/build.gradle.kts").readText().contains("implementation(libs.pinyin4j)"))
    }
}
