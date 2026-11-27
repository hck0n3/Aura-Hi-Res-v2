package iad1tya.echo.music.eq.autoeq

import iad1tya.echo.music.eq.data.FilterType
import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 915-916: the bundled AutoEq catalogue (`assets/autoeq_profiles.tsv.gz`, built by
 * `scripts/build_autoeq_profiles.py`) and the Bluetooth-name matching that runs on it, against the real file.
 */
class AutoEqOfflineCatalogTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }
    private val asset by lazy { File(root, "app/src/main/assets/${AutoEqOfflineCatalog.ASSET_NAME}") }
    private val catalogue by lazy {
        GZIPInputStream(asset.inputStream()).bufferedReader().useLines { AutoEqOfflineCatalog.parse(it) }
    }

    /** The real AutoEq file: results/oratory1990/over-ear/Sennheiser HD 650/Sennheiser HD 650 ParametricEQ.txt. */
    private val hd650 = """
        Preamp: -6.1 dB
        Filter 1: ON LSC Fc 105 Hz Gain 6.4 dB Q 0.70
        Filter 2: ON PK Fc 8800 Hz Gain 5.1 dB Q 1.42
        Filter 3: ON PK Fc 118 Hz Gain -3.1 dB Q 0.50
        Filter 4: ON PK Fc 37 Hz Gain 0.7 dB Q 3.96
        Filter 5: ON PK Fc 3169 Hz Gain -1.7 dB Q 3.89
        Filter 6: ON HSC Fc 10000 Hz Gain -2.1 dB Q 0.70
        Filter 7: ON PK Fc 1227 Hz Gain -1.2 dB Q 2.53
        Filter 8: ON PK Fc 2055 Hz Gain 1.2 dB Q 3.23
        Filter 9: ON PK Fc 587 Hz Gain 0.4 dB Q 1.19
        Filter 10: ON PK Fc 5332 Hz Gain -1.1 dB Q 5.75
    """.trimIndent()

    @Test fun `the bundled catalogue is compact and complete`() {
        assertTrue("asset size ${asset.length()} must stay within ~600 KB", asset.length() <= 600 * 1024)
        assertTrue("models: ${catalogue.size}", catalogue.size >= 8000)
        assertTrue(catalogue.all { it.toProfile().bands.isNotEmpty() })
        assertTrue(catalogue.count { it.recommended } >= 3000)
        assertTrue(catalogue.all { it.type != AutoEqRigType.UNKNOWN })
    }

    @Test fun `an offline profile is the same filters as the downloaded file`() {
        val offline = catalogue.single { it.name == "Sennheiser HD 650" && it.source == "oratory1990" }
        val parsed = AutoEqParser.parse(hd650)
        assertEquals(parsed, offline.toProfile())
        assertEquals(FilterType.LSC, offline.toProfile().bands.first().type)
        // A device assignment keeps its own copy of the curve in the same format.
        assertEquals(offline, AutoEqOfflineCatalog.parseLine(offline.toLine()))
    }

    @Test fun `the online index row finds its bundled measurement by name, source and rig kind`() {
        val hit = AutoEqOfflineCatalog.pick(
            catalogue, "Sennheiser HD 650", "oratory1990",
            "results/oratory1990/over-ear/Sennheiser HD 650/Sennheiser HD 650 ParametricEQ.txt",
        )
        assertNotNull(hit)
        assertEquals(-6.1, hit!!.preampDb, 1e-9)
        assertNull(
            AutoEqOfflineCatalog.pick(
                catalogue, "Sennheiser HD 650", "oratory1990",
                "results/oratory1990/in-ear/Sennheiser HD 650/Sennheiser HD 650 ParametricEQ.txt",
            ),
        )
    }

    @Test fun `malformed lines are skipped`() {
        assertNull(AutoEqOfflineCatalog.parseLine("# autoeq 7ae0f56"))
        assertNull(AutoEqOfflineCatalog.parseLine("only\ttwo"))
        assertNull(AutoEqOfflineCatalog.parseLine("A\tB\tin-ear\t1\tnot-a-number\tP100,1.0,1.00"))
        assertEquals(
            listOf(AutoEqBand(100.0, 1.0, 1.0, FilterType.PK)),
            AutoEqOfflineCatalog.parseBands("P100,1.0,1.00;X5,1,1;P0,1.0,1.0;H"),
        )
    }

    private fun source(path: String): String = File(root, path).readText()

    @Test fun `the Auto-EQ list applies the bundled profile before trying the network`() {
        val repo = source("app/src/main/kotlin/com/music/echo/eq/autoeq/AutoEqRepository.kt")
        val fetch = repo.substringAfter("suspend fun fetchProfile(entry: AutoEqEntry)")
        assertTrue(fetch.indexOf("AutoEqOfflineCatalog.find(") in 0 until fetch.indexOf("httpGet("))
    }

    @Test fun `the profiles are credited in the app and in the notices`() {
        val screen = source("app/src/main/kotlin/com/music/echo/ui/screens/equalizer/autoeq/AutoEqScreen.kt")
        assertTrue(screen.contains("stringResource(R.string.autoeq_credits)"))
        for (res in listOf("values", "values-es")) {
            val strings = source("app/src/main/res/$res/strings.xml")
            val credits = strings.substringAfter("name=\"autoeq_credits\">").substringBefore("</string>")
            assertTrue(res, credits.contains("AutoEq") && credits.contains("MIT"))
            assertTrue(res, credits.contains("oratory1990") && credits.contains("crinacle") && credits.contains("Rtings"))
        }
        val notices = source("THIRD-PARTY-NOTICES.md")
        assertTrue(notices.contains("jaakkopasanen/AutoEq") && notices.contains("autoeq_profiles.tsv.gz"))
        assertTrue(notices.contains("Copyright (c) 2018-2022 Jaakko Pasanen"))
    }
}
