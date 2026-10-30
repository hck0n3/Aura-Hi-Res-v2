package iad1tya.echo.music.license

import org.junit.Assert.assertEquals
import org.junit.Test

class LicenseStatusParserTest {

    @Test fun active() {
        assertEquals(LicenseStatus.ACTIVE, LicenseStatusParser.parse("""{"status":"active"}"""))
    }

    @Test fun ended() {
        assertEquals(LicenseStatus.ENDED, LicenseStatusParser.parse("""{"status":"ended"}"""))
    }

    @Test fun invalid() {
        assertEquals(LicenseStatus.INVALID_KEY, LicenseStatusParser.parse("""{"status":"invalid"}"""))
    }

    @Test fun deviceMismatch() {
        assertEquals(
            LicenseStatus.DEVICE_MISMATCH,
            LicenseStatusParser.parse("""{"status":"device_mismatch"}"""),
        )
    }

    @Test fun unknownStatusIsNetworkError() {
        assertEquals(LicenseStatus.NETWORK_ERROR, LicenseStatusParser.parse("""{"status":"error"}"""))
    }

    @Test fun garbageBodyIsNetworkError() {
        assertEquals(LicenseStatus.NETWORK_ERROR, LicenseStatusParser.parse("<html>502</html>"))
    }

    @Test fun emailMismatch() {
        assertEquals(
            LicenseStatus.EMAIL_MISMATCH,
            LicenseStatusParser.parse("""{"status":"email_mismatch"}"""),
        )
    }

    @Test fun emailVerifiedOnlyWhenTheWorkerSaysSo() {
        assertEquals(true, LicenseStatusParser.emailVerified("""{"status":"active","email_ok":true}"""))
        // a master key, an old Worker, no email typed, or garbage: never verified
        assertEquals(false, LicenseStatusParser.emailVerified("""{"status":"active"}"""))
        assertEquals(false, LicenseStatusParser.emailVerified("""{"status":"active","email_ok":false}"""))
        assertEquals(false, LicenseStatusParser.emailVerified("<html>502</html>"))
    }
}
