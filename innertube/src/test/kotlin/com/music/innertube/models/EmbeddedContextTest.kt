package com.music.innertube.models

import com.music.innertube.models.response.PlayerResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedContextTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val locale = YouTubeLocale(gl = "US", hl = "en")

    @Test
    fun `an embedded context names the EMBED screen and the third party, a normal one names neither`() {
        val embedded = YouTubeClient.WEB_EMBEDDED_PLAYER.toContext(locale, null, null).asEmbedded("https://example.org/")
        val text = json.encodeToString(Context.serializer(), embedded)
        assertTrue(text, text.contains("\"clientScreen\":\"EMBED\""))
        assertTrue(text, text.contains("\"embedUrl\":\"https://example.org/\""))

        val plain = json.encodeToString(Context.serializer(), YouTubeClient.TVHTML5_SAMSUNG.toContext(locale, null, null))
        assertFalse(plain, plain.contains("clientScreen"))
        assertFalse(plain, plain.contains("thirdParty"))
    }

    @Test
    fun `an age-gated response is recognised by desktopLegacyAgeGateReason whatever language its reason is in`() {
        val status = json.decodeFromString(
            PlayerResponse.PlayabilityStatus.serializer(),
            """{"status":"LOGIN_REQUIRED","reason":"Accede para confirmar tu edad","desktopLegacyAgeGateReason":1}""",
        )
        assertTrue(status.desktopLegacyAgeGateReason != null)
        val generic = json.decodeFromString(PlayerResponse.PlayabilityStatus.serializer(), """{"status":"LOGIN_REQUIRED"}""")
        assertTrue(generic.desktopLegacyAgeGateReason == null)
    }
}
