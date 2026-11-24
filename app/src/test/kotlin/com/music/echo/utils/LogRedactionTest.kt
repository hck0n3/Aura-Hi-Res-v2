package iad1tya.echo.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shareable log is emailed to the owner by paying customers. A credential in it is a real leak —
 * one already shipped (PoTokenWebView logged a full dataSyncId at ERROR). These tests pin the three
 * the owner called out by name — a URL with `?pot=`, a cookie header, and a dataSyncId — plus the
 * neighbours that would leak the same account.
 */
class LogRedactionTest {

    private fun assertScrubbed(secret: String, input: String) {
        val out = LogRedaction.redact(input)
        assertFalse("leaked <$secret> in: $out", out.contains(secret))
        assertTrue("nothing was redacted in: $out", out.contains("<redacted>"))
    }

    // ── The three the owner named ────────────────────────────────────────────────────────────────

    @Test
    fun poTokenQueryParamIsRedacted() {
        assertScrubbed(
            "MnQ1jVLPd6cIsBKF7A9x",
            "java.io.IOException: unexpected end of stream for " +
                "https://rr3---sn-4g5e6nz7.googlevideo.com/videoplayback?expire=1754236800&" +
                "id=abc123&pot=MnQ1jVLPd6cIsBKF7A9x&mime=audio%2Fwebm",
        )
    }

    @Test
    fun poTokenRedactionKeepsTheRestOfTheUrlDiagnosable() {
        // Over-redaction is safe, but scrubbing the whole URL would destroy the videoId and the expiry
        // that make a stream failure attributable at all.
        val out = LogRedaction.redact(
            "GET https://rr3.googlevideo.com/videoplayback?expire=1754236800&id=abc123&pot=SECRETVALUE&cpn=xyz"
        )
        assertTrue(out, out.contains("expire=1754236800"))
        assertTrue(out, out.contains("id=abc123"))
        assertTrue(out, out.contains("cpn=xyz"))
        assertTrue(out, out.contains("pot=<redacted>"))
    }

    @Test
    fun theSignatureCipherParamsAreRedacted() {
        // `sig=` and the bare `s=` are the signatureCipher pair. `s=` is one character, so it is the
        // easiest one for a narrower regex to miss.
        assertScrubbed("SIGNATUREVALUE", "https://rr1.googlevideo.com/videoplayback?id=abc&sig=SIGNATUREVALUE")
        assertScrubbed("CIPHERVALUE", "https://rr1.googlevideo.com/videoplayback?id=abc&s=CIPHERVALUE&sp=sig")
    }

    @Test
    fun cookieHeaderIsRedactedIncludingEveryPairAfterTheFirst() {
        val out = LogRedaction.redact(
            "Cookie: SAPISID=aaaaaaaaaaaa; __Secure-3PAPISID=bbbbbbbbbbbb; LOGIN_INFO=cccccccccccc"
        )
        // The `;`-separated tail is exactly what a "stop at the first delimiter" rule would have left
        // in the clear.
        assertFalse(out, out.contains("aaaaaaaaaaaa"))
        assertFalse(out, out.contains("bbbbbbbbbbbb"))
        assertFalse(out, out.contains("cccccccccccc"))
        assertEquals("Cookie: <redacted>", out)
    }

    @Test
    fun cookieHeaderRedactionStopsAtTheEndOfItsOwnLine() {
        val out = LogRedaction.redact("Cookie: SAPISID=secretvalue\nPlaying videoId=_KZEkEb_dvA")
        assertFalse(out, out.contains("secretvalue"))
        assertTrue("swallowed the next line: $out", out.contains("Playing videoId=_KZEkEb_dvA"))
    }

    @Test
    fun dataSyncIdIsRedacted() {
        assertScrubbed("abcdef123456", "dataSyncId=abcdef123456 visitor session started")
        assertScrubbed("abcdef123456", "data_sync_id: abcdef123456")
        assertScrubbed("abcdef123456", """{"dataSyncId":"abcdef123456","clientName":"ANDROID_MUSIC"}""")
    }

    /**
     * The shape that actually leaked (PoTokenWebView's `generatePoToken($identifier)` at ERROR) was a
     * session id with NO key next to it, and a bare opaque string is indistinguishable from a playlist
     * id — no chokepoint can catch that in general. It is fixed at the call site by logging only the
     * length, and this pins that the length form carries nothing recoverable.
     */
    @Test
    fun theLengthOnlyFormOfAnIdentifierCarriesNoSecret() {
        val line = "generatePoToken timed out after 10000ms (idLen=94)"
        assertEquals(line, LogRedaction.redact(line))
    }

    // ── The neighbours that leak the same account ───────────────────────────────────────────────

    @Test
    fun bareCookiePairsWithNoHeaderPrefixAreRedacted() {
        assertScrubbed("aaaaaaaaaaaaaaaa", "restoring session SAPISID=aaaaaaaaaaaaaaaa for request")
    }

    @Test
    fun authorizationHeaderAndBearerTokenAreRedacted() {
        assertScrubbed("ya29.a0AfH6SMBx", "Authorization: Bearer ya29.a0AfH6SMBx")
        assertScrubbed("ya29.a0AfH6SMBx", "request failed with Bearer ya29.a0AfH6SMBx")
        assertScrubbed("1a2b3c4d5e6f", "SAPISIDHASH 1754236800_1a2b3c4d5e6f")
    }

    @Test
    fun visitorDataIsRedacted() {
        assertScrubbed("CgtBQkNERUZHSElKSw", """{"visitorData":"CgtBQkNERUZHSElKSw"}""")
        assertScrubbed("CgtBQkNERUZHSElKSw", "X-Goog-Visitor-Id: CgtBQkNERUZHSElKSw")
    }

    @Test
    fun accountEmailIsRedacted() {
        assertScrubbed("toberto4000@gmail.com", "sync failed for account toberto4000@gmail.com")
    }

    @Test
    fun licenceKeysAreRedacted() {
        // Gumroad grouped form and the plain UUID form.
        assertScrubbed("A1B2C3D4-E5F6A7B8-C9D0E1F2-A3B4C5D6", "licence check for A1B2C3D4-E5F6A7B8-C9D0E1F2-A3B4C5D6 failed")
        assertScrubbed("550e8400-e29b-41d4-a716-446655440000", "key=550e8400-e29b-41d4-a716-446655440000")
        assertScrubbed("hunter2hunter2", "licenseKey=hunter2hunter2 rejected")
    }

    @Test
    fun accessAndRefreshTokensAreRedacted() {
        assertScrubbed("tok_abcdef123456", """{"access_token":"tok_abcdef123456"}""")
        assertScrubbed("ref_abcdef123456", "refresh_token=ref_abcdef123456")
        assertScrubbed("qobuzsecret123", "?app_secret=x&access_token=qobuzsecret123")
    }

    @Test
    fun apiKeyQueryParamIsRedacted() {
        assertScrubbed("AIzaSyDummyKeyValue123", "https://youtubei.googleapis.com/youtubei/v1/player?key=AIzaSyDummyKeyValue123")
    }

    @Test
    fun aBareOpaqueBlobIsRedacted() {
        // The backstop for a raw token printed with no key at all to key off.
        val blob = "A".repeat(140)
        assertScrubbed(blob, "poToken generated $blob")
    }

    @Test
    fun contentDocumentPathsAreRedactedButTheAuthorityStays() {
        val out = LogRedaction.redact(
            "open failed: content://com.android.externalstorage.documents/document/primary%3AMusic%2FMyBand%2Fsong.flac (EACCES)"
        )
        assertFalse(out, out.contains("MyBand"))
        assertFalse(out, out.contains("song.flac"))
        assertTrue(out, out.contains("content://com.android.externalstorage.documents/<redacted>"))
        assertTrue(out, out.contains("(EACCES)"))
    }

    @Test
    fun mediaStoreContentUrisCarryOnlyANumberAndStay() {
        val line = "Source error for content://media/external/audio/media/1234"
        assertEquals(line, LogRedaction.redact(line))
    }

    @Test
    fun credentialsInsideAUrlAreRedactedButTheHostStays() {
        val out = LogRedaction.redact("GET https://alice:s3cretPass@music.example.org/rest/stream failed")
        assertFalse(out, out.contains("alice"))
        assertFalse(out, out.contains("s3cretPass"))
        assertTrue(out, out.contains("https://<redacted>@music.example.org/rest/stream"))
        val port = "connect to http://music.example.org:4533/rest failed"
        assertEquals(port, LogRedaction.redact(port))
    }

    @Test
    fun forLogKeepsYouTubeIdsAndHashesEverythingElse() {
        assertEquals("_KZEkEb_dvA", LogRedaction.forLog("_KZEkEb_dvA"))
        assertEquals("null", LogRedaction.forLog(null))
        val local = "content://com.android.externalstorage.documents/document/primary%3AMusic%2FMyBand%2Fsong.flac"
        val out = LogRedaction.forLog(local)
        assertFalse(out, out.contains("MyBand"))
        assertTrue(out, out.startsWith("content#"))
        assertEquals(out, LogRedaction.forLog(local))
        assertTrue(LogRedaction.forLog("/storage/emulated/0/Music/song.flac").startsWith("id#"))
    }

    @Test
    fun storagePathsKeepTheVolumeRootAndLoseTheRest() {
        val out = LogRedaction.redact(
            "java.io.FileNotFoundException: /storage/emulated/0/Music/My Band/My Song.flac: open failed: ENOENT (No such file or directory)"
        )
        assertFalse(out, out.contains("My Band"))
        assertFalse(out, out.contains("Song.flac"))
        assertTrue(out, out.contains("/storage/emulated/0/<redacted>: open failed: ENOENT (No such file or directory)"))

        val sd = LogRedaction.redact("open /storage/1A2B-3C4D/Music/song.flac failed\nnext line")
        assertFalse(sd, sd.contains("song.flac"))
        assertTrue(sd, sd.contains("/storage/1A2B-3C4D/<redacted>"))
        assertTrue("swallowed the next line: $sd", sd.endsWith("\nnext line"))

        val sdcard = LogRedaction.redact("read /sdcard/Download/mixtape.mp3")
        assertEquals("read /sdcard/<redacted>", sdcard)
    }

    @Test
    fun fileUrisAreRedacted() {
        val storage = LogRedaction.redact("Source error for file:///storage/emulated/0/Music/song.flac")
        assertEquals("Source error for file:///storage/emulated/0/<redacted>", storage)
        val other = LogRedaction.redact("Source error for file:///data/user/0/pkg/files/My Song.flac")
        assertEquals("Source error for file:///data/<redacted>", other)
    }

    @Test
    fun pathRulesAreIdempotent() {
        val once = LogRedaction.redact(
            "a /storage/emulated/0/Music/x.flac: b file:///storage/emulated/0/y.flac c\n" +
                "file:///data/user/0/z.flac /sdcard/w.mp3 /storage/1A2B-3C4D/v.flac"
        )
        assertFalse(once, once.contains(".flac"))
        assertFalse(once, once.contains(".mp3"))
        assertEquals(once, LogRedaction.redact(once))
    }

    @Test
    fun forLogKeepsMediaStoreUrisLikeRedactDoes() {
        val uri = "content://media/external/audio/media/1234"
        assertEquals(uri, LogRedaction.forLog(uri))
        assertEquals(LogRedaction.redact(uri), LogRedaction.forLog(uri))
        assertTrue(LogRedaction.forLog("content://media/external/audio/media/1234/albumart/x").startsWith("content#"))
    }

    @Test
    fun newRulesAreIdempotent() {
        val once = LogRedaction.redact(
            "https://bob:pw@host.example/x content://com.android.providers.downloads.documents/document/msf%3A12?key=abc"
        )
        assertEquals(once, LogRedaction.redact(once))
    }

    // ── Over-redaction guards: the log must stay diagnosable ────────────────────────────────────

    @Test
    fun ordinaryDiagnosticLinesAreLeftAlone() {
        val untouched = listOf(
            "RESOLVE_TIMING videoId=_KZEkEb_dvA total=842ms client=ANDROID_MUSIC ok",
            "CROSSFADE_TRACE swap begin from=_KZEkEb_dvA to=dQw4w9WgXcQ at=4820ms",
            "NO_REPEAT contextId=PL:RDAMVM_KZEkEb_dvA pool=42 played=17",
            "Playback failed: MediaCodecRenderer error code 3003 at position 12045ms",
            "java.net.SocketTimeoutException: timeout at okhttp3.internal.http2.Http2Stream.takeHeaders",
        )
        untouched.forEach { assertEquals("wrongly redacted: $it", it, LogRedaction.redact(it)) }
    }

    @Test
    fun theWordCookieInProseIsNotTreatedAsAHeader() {
        val line = "cookie refresh failed after 3 attempts"
        assertEquals(line, LogRedaction.redact(line))
    }

    @Test
    fun videoAndPlaylistIdsAreShortEnoughToSurviveTheOpaqueBlobRule() {
        val line = "queue seeded from PLZbXA4lyCtqoWi2FS6MKfPfmdWjs1Zj0k with videoId _KZEkEb_dvA"
        assertEquals(line, LogRedaction.redact(line))
    }

    // ── Rows 844-845: the Amazon Music session (cookies + web-player headers) ────────────────────────

    @Test
    fun amazonMusicSessionCookiesAndHeadersAreRedacted() {
        assertScrubbed("Atza|IwEBIA", "renewal with at-main=Atza|IwEBIA failed")
        assertScrubbed("sessTOKEN123", "session-token=sessTOKEN123; ubid-acbes=262-1")
        assertScrubbed("262-1234567", "ubid-acbes=262-1234567 rotated")
        assertScrubbed("xMAINvalue", "x-main=xMAINvalue")
        assertScrubbed("eyJkZXZpY2VJZCI6Ij/abc", "GraphQL rejected AmznMusic eyJkZXZpY2VJZCI6Ij/abc")
        assertScrubbed("sess-9f8e", "x-amzn-session-id: sess-9f8e")
        assertScrubbed("panda-tok", """{"accessToken":"panda-tok","expiresIn":3600}""")
    }

    @Test
    fun aHyphenatedWordEndingInAtIsNotAnAmazonCookie() {
        val line = "Amazon collection loaded: 12 collections, dropped-at-5=0 retried-x-1=2"
        assertEquals(line, LogRedaction.redact(line))
    }

    @Test
    fun emptyInputIsReturnedUnchanged() {
        assertEquals("", LogRedaction.redact(""))
    }

    @Test
    fun redactionIsIdempotent() {
        val once = LogRedaction.redact("Cookie: SAPISID=secret\ndataSyncId=abc123456789")
        assertEquals(once, LogRedaction.redact(once))
    }
}
