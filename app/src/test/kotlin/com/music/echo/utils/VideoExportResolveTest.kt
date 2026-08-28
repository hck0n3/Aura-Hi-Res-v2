package iad1tya.echo.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins HALLAZGO-035: "export as video" used to resolve ONLY through the burned InnerTube TVHTML5
 * path (videoStreamUrlDiag), while the in-app video mode plays fine through NewPipe's ANDROID_VR
 * extraction. The export/probe/download chains now share one preference: NewPipe first, the
 * InnerTube diag result only as fallback. This pins that preference order.
 */
class VideoExportResolveTest {

    @Test
    fun `the NewPipe url wins when both resolvers deliver`() {
        assertEquals(
            "https://pipe.example/stream",
            pickVideoExportUrl("https://pipe.example/stream", "https://tube.example/stream"),
        )
    }

    @Test
    fun `a blank or missing NewPipe url falls back to InnerTube`() {
        assertEquals("https://tube.example/stream", pickVideoExportUrl(null, "https://tube.example/stream"))
        assertEquals("https://tube.example/stream", pickVideoExportUrl("", "https://tube.example/stream"))
        assertEquals("https://tube.example/stream", pickVideoExportUrl("   ", "https://tube.example/stream"))
    }

    @Test
    fun `no usable url from either resolver yields null`() {
        assertNull(pickVideoExportUrl(null, null))
        assertNull(pickVideoExportUrl("", "  "))
    }
}
