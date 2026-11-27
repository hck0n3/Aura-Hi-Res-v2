package iad1tya.echo.music.eq.autoeq

import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 915: Bluetooth names matched against the real bundled catalogue — local, and never a guess. */
class AutoEqNameMatcherTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }
    private val catalogue by lazy {
        GZIPInputStream(File(root, "app/src/main/assets/${AutoEqOfflineCatalog.ASSET_NAME}").inputStream())
            .bufferedReader().useLines { AutoEqOfflineCatalog.parse(it) }
    }

    private fun matched(name: String) = AutoEqNameMatcher.match(name, catalogue)

    @Test fun `common Bluetooth names find their headphone`() {
        val xm4 = matched("WH-1000XM4")!!
        assertEquals("Sony WH-1000XM4", xm4.model)
        assertEquals("oratory1990", xm4.entry.source)
        assertEquals(AutoEqRigType.OVER_EAR, xm4.entry.type)
        assertEquals("Sony WH-1000XM4", matched("LE_WH-1000XM4")!!.model)
        assertEquals("Sony WH-1000XM4", matched("WH-1000XM4 Hands-Free")!!.model)

        val buds = matched("Galaxy Buds2 Pro")!!
        assertEquals("Samsung Galaxy Buds2 Pro", buds.model)
        assertEquals(AutoEqRigType.IN_EAR, buds.entry.type)
        assertEquals("Samsung Galaxy Buds2 Pro", matched("Galaxy Buds2 Pro (A1B2)")!!.model)

        val airpods = matched("AirPods Pro")!!
        assertEquals("Apple AirPods Pro", airpods.model)
        assertTrue(airpods.entry.recommended)
        // The owner's own name, before or after the model, never changes the model.
        assertEquals("Apple AirPods Pro", matched("Juan's AirPods Pro")!!.model)
        assertEquals("Apple AirPods Pro", matched("AirPods Pro de Ana")!!.model)
    }

    @Test fun `a near name never falls back to another model`() {
        assertEquals("Samsung Galaxy Buds2", matched("Galaxy Buds2")!!.model)
        assertEquals("Sony WF-1000XM4", matched("WF-1000XM4")!!.model)
        assertEquals(AutoEqRigType.IN_EAR, matched("WF-1000XM4")!!.entry.type)
        // «(2)» is part of this product's name; with no neutral «Nothing Ear (2)» measurement there is no guess.
        assertNull(matched("Nothing Ear (2)"))
        assertNull(matched("JBL Flip 5"))
        assertNull(matched("Car Kit"))
        assertNull(matched(""))
    }

    @Test fun `presets, ear tips and volume levels are never picked automatically`() {
        val all = catalogue.filter { AutoEqNameMatcher.splitVariant(it.name).first == "Apple AirPods Pro 2" }
        assertTrue(all.any { AutoEqNameMatcher.splitVariant(it.name).second.contains("db") })
        val pick = matched("AirPods Pro 2")!!
        val variant = AutoEqNameMatcher.splitVariant(pick.entry.name).second
        assertTrue("picked $variant", variant.isEmpty() || variant == "anc on" || variant == "anc mode")
    }
}
