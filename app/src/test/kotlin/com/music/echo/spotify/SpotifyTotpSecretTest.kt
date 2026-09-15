package iad1tya.echo.music.spotify

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The TOTP secret derivation, pinned.
 *
 * A Spotify web-player token is minted from a 6-digit TOTP, and a secret that differs from
 * SimpMusic's by ONE byte mints nothing at all — the request comes back as an anonymous token and
 * every Spotify lyric then reports "no lyrics", with no error anywhere. The derivation is also the
 * one part of the flow that can be checked without a network or an account, so it is checked here.
 *
 * The expected value is the base32 of SimpMusic's own `TOTP_SECRET_V22` after its transform
 * (`byte xor ((index % 33) + 9)`, the results concatenated as decimal text). Upstream then runs
 * those bytes through hex and base64 before base32; both are exact round trips, which is why this
 * port skips them — and this test is what proves skipping them changed nothing.
 */
class SpotifyTotpSecretTest {
    @Test
    fun `the built-in cipher derives to SimpMusic's secret`() {
        assertEquals(
            "GEYDMMJRGEYTENBRGE4TOMRRGE4DQNBRGA3TCMJSHA4DGOBTGY4DSMJRGIZTEOJTGEYTSNBU",
            SpotifyAuth.secretFromCipherBytes(SpotifyAuth.BUILTIN_SECRET_CIPHER),
        )
    }

    @Test
    fun `the built-in secret is version 22, the one upstream ships`() {
        assertEquals(22, SpotifyAuth.BUILTIN_SECRET_VERSION)
    }

    @Test
    fun `the transform is index-dependent, so byte order matters`() {
        // Reversing the cipher must NOT produce the same secret: the XOR key walks with the index,
        // so a derivation that ignored the position would pass everything above and still be wrong.
        val reversed = SpotifyAuth.BUILTIN_SECRET_CIPHER.reversed()
        assert(
            SpotifyAuth.secretFromCipherBytes(reversed) !=
                SpotifyAuth.secretFromCipherBytes(SpotifyAuth.BUILTIN_SECRET_CIPHER),
        )
    }
}
