package iad1tya.echo.music.echomusic.updater

import iad1tya.echo.music.echomusic.updater.UpdateApkFiles.ReleaseApk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Row 331 (plan B4): which release APK each device downloads. */
class UpdateAssetPickTest {

    private val arm64 = ReleaseApk("Aura-Hi-Res-v2-2.0.65-arm64.apk", "a", 46)
    private val universal = ReleaseApk("Aura-Hi-Res-v2-2.0.65-universal.apk", "u", 87)
    private val unlabeled = ReleaseApk("Aura-Hi-Res-v2-2.0.63.apk", "o", 87)
    private val changelog = ReleaseApk("changelog.json", "c", 3)

    private val phone = listOf("arm64-v8a", "armeabi-v7a", "armeabi")
    private val phone64Only = listOf("arm64-v8a")
    private val tv32 = listOf("armeabi-v7a", "armeabi")
    private val chromebook = listOf("x86_64", "x86", "arm64-v8a", "armeabi-v7a")

    private fun pick(assets: List<ReleaseApk>, abis: List<String>) = UpdateApkFiles.pickApkAsset(assets, abis)

    @Test
    fun arm64PhoneTakesTheSmallApk() {
        assertEquals(arm64, pick(listOf(universal, arm64, changelog), phone))
        assertEquals(arm64, pick(listOf(arm64, universal), phone64Only))
    }

    @Test
    fun everyOtherDeviceTakesTheUniversal() {
        assertEquals(universal, pick(listOf(arm64, universal), tv32))
        // ARM translation (Chromebook, WSA): native x86 libs beat translated arm64 (heat, battery).
        assertEquals(universal, pick(listOf(arm64, universal), chromebook))
    }

    @Test
    fun releasesBeforeB4StillWork() {
        assertEquals(unlabeled, pick(listOf(unlabeled, changelog), phone))
        assertEquals(unlabeled, pick(listOf(unlabeled, changelog), tv32))
        assertEquals(universal, pick(listOf(universal), phone))
    }

    @Test
    fun anApkTheDeviceCannotRunIsNeverOffered() {
        assertNull(pick(listOf(arm64), tv32))
        assertNull(pick(listOf(arm64), chromebook))
    }

    @Test
    fun orderDoesNotMatter() {
        listOf(phone, tv32, chromebook).forEach { abis ->
            val expected = pick(listOf(arm64, universal, changelog), abis)
            listOf(
                listOf(universal, arm64, changelog),
                listOf(changelog, universal, arm64),
                listOf(arm64, changelog, universal),
            ).forEach { assertEquals(expected, pick(it, abis)) }
        }
    }

    @Test
    fun testAndAuxiliaryFilesAreNeverChosen() {
        val debug = ReleaseApk("Aura-Hi-Res-TEST-abc1234-debug.apk", "d", 1)
        val nosub = ReleaseApk("Aura-Hi-Res-v2-2.0.65-NOSUB.apk", "n", 1)
        val part = ReleaseApk("Aura-Hi-Res-v2-2.0.65.apk.part", "p", 1)
        assertNull(pick(listOf(debug, nosub, part, changelog), phone))
    }

    /** The rule every version installed before B4 uses; the CI names must satisfy it in any order. */
    private fun legacyPick(assets: List<ReleaseApk>, abis: List<String>): ReleaseApk? {
        val apks = assets.filter { it.name.endsWith(".apk") && !it.name.lowercase().contains("debug") }
        fun ReleaseApk.isUniversal() = name.lowercase().contains("universal")
        return if (abis.any { it == "arm64-v8a" }) apks.firstOrNull { !it.isUniversal() } ?: apks.firstOrNull()
        else apks.firstOrNull { it.isUniversal() } ?: apks.firstOrNull()
    }

    @Test
    fun namingContractKeepsOldInstallsCorrect() {
        listOf(listOf(arm64, universal), listOf(universal, arm64)).forEach { order ->
            assertEquals(arm64, legacyPick(order, phone))
            assertEquals(universal, legacyPick(order, tv32))
        }
    }
}
