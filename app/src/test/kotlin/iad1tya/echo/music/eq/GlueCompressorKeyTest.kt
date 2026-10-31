package iad1tya.echo.music.eq

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 610: the glue compressor's detector must not be driven by the bass (it ducked the voice whenever a bass note hit). */
class GlueCompressorKeyTest {
    @Test
    fun theCompressorKeyIgnoresTheBass() {
        val src = File("src/main/cpp/superpowered/SuperpoweredBridge.cpp").readText()
        val hp = Regex("glueCompressor->hpCutOffHz\\s*=\\s*([0-9.]+)f").find(src)?.groupValues?.get(1)?.toFloat()
        assertTrue("glue compressor key high-pass missing", hp != null)
        assertTrue("key high-pass must be at least 150 Hz so bass does not duck the voice, was $hp", hp!! >= 150f)
    }
}
