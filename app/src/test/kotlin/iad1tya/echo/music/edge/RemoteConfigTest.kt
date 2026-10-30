package iad1tya.echo.music.edge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase A: the config is trusted only when the Worker's signature verifies. The vector below was signed by the real Worker code path (WebCrypto ECDSA P-256). */
class RemoteConfigTest {
    private val pub = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEIldMh5tTHJBOmwEdrszzYzYqTMbVD4DZ3Vcbq9ynDBfgeOYCq0dRcH0OcsnLNMo8wwjQDGM65x0oAJOsRs3QMw=="
    private val payload = "{\"v\":1,\"issuedAt\":1760000000000,\"flags\":{\"letras\":false,\"cola_inteligente\":true,\"inventada\":false,\"reproduccion\":false},\"notice\":{\"id\":\"n1\",\"titulo\":\"Hola\",\"texto\":\"Mantenimiento\",\"hasta\":0},\"minVersion\":1088}"
    private val sig = "kip6ezJbua0TtosWlFgT7NmTvzLrAGK8WvhGAvxM06A97/lRywaaYVabWAgm0Rs7NaYyMQzFa8WhklNbpmlTPQ=="
    private fun envelope(p: String = payload, s: String = sig) =
        "{\"payload\":" + kotlinx.serialization.json.JsonPrimitive(p).toString() + ",\"sig\":\"" + s + "\"}"

    @Test
    fun `a signature made by the Worker verifies and a changed payload does not`() {
        assertTrue(EdgeSignature.verify(pub, payload, sig))
        assertFalse(EdgeSignature.verify(pub, payload.replace("false", "true"), sig))
        assertFalse(EdgeSignature.verify("", payload, sig))
        assertFalse(EdgeSignature.verify(pub, payload, "AAAA"))
    }

    @Test
    fun `an accepted config keeps known flags only and the notice`() {
        val c = RemoteConfigs.accept(envelope(), pub)
        assertNotNull(c)
        assertEquals(mapOf("letras" to false, "cola_inteligente" to true), c!!.flags)
        assertEquals("n1", c.notice?.id)
        assertEquals(1088, c.minVersion)
    }

    @Test
    fun `nothing is installed without a valid signature`() {
        assertNull(RemoteConfigs.accept(envelope(s = "AAAA"), pub))
        assertNull(RemoteConfigs.accept(envelope(), ""))
        assertNull(RemoteConfigs.accept("not json", pub))
    }

    @Test
    fun `without a config every function is on, and a flag switches only itself off`() {
        RemoteConfigs.install(null)
        assertTrue(RemoteConfigs.on("letras"))
        RemoteConfigs.install(RemoteConfigs.accept(envelope(), pub))
        assertFalse(RemoteConfigs.on("letras"))
        assertTrue(RemoteConfigs.on("cola_inteligente"))
        assertTrue("an unknown name stays on", RemoteConfigs.on("reproduccion"))
        RemoteConfigs.install(null)
    }

    @Test
    fun `a notice shows once per id and stops at its date`() {
        RemoteConfigs.install(RemoteConfigs.accept(envelope(), pub))
        assertEquals("n1", RemoteConfigs.noticeToShow(1L, null)?.id)
        assertNull(RemoteConfigs.noticeToShow(1L, "n1"))
        RemoteConfigs.install(RemoteConfig(emptyMap(), EdgeNotice("n2", "t", "x", 1000L), 0, 0))
        assertNotNull(RemoteConfigs.noticeToShow(999L, null))
        assertNull(RemoteConfigs.noticeToShow(1000L, null))
        RemoteConfigs.install(null)
    }

    @Test
    fun `a raw r-s signature becomes DER`() {
        assertNull(EdgeSignature.rawToDer(ByteArray(10)))
        val der = EdgeSignature.rawToDer(ByteArray(64) { 0x7f })!!
        assertEquals(0x30, der[0].toInt())
        assertEquals(der.size - 2, der[1].toInt())
    }
}
