package iad1tya.echo.music.ui.screens.welcome

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import iad1tya.echo.music.constants.LastOpenedVersionCodeKey
import iad1tya.echo.music.constants.OnboardingArtistsDoneKey
import iad1tya.echo.music.constants.TermsAcceptedAppVersionKey
import iad1tya.echo.music.constants.WelcomeDoneKey
import iad1tya.echo.music.constants.WelcomePermissionsDoneKey
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 871/872 (plan phase 2): the new welcome is shown to a fresh install exactly once, and an install updated
 * from beta12 or earlier never sees any of it — decided from keys only an earlier build could have written.
 */
class WelcomeFlowTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun show(welcomeDone: Boolean = false, lastOpened: Int = -1, artistsDone: Boolean = false, termsApp: Int = 0) =
        WelcomeFlow.showWelcome(welcomeDone, lastOpened, artistsDone, termsApp)

    private fun permissionsStep(
        permissionsDone: Boolean = false,
        welcomeDone: Boolean = false,
        lastOpened: Int = -1,
        artistsDone: Boolean = false,
        termsApp: Int = 0,
    ) = WelcomeFlow.showPermissionsStep(permissionsDone, welcomeDone, lastOpened, artistsDone, termsApp)

    @Test
    fun `an existing user updated from beta12 never sees the welcome`() {
        // The usual case: the app was opened before, so LastOpenedVersionCodeKey holds a versionCode.
        assertFalse(show(lastOpened = 1111, termsApp = 1111))
        assertFalse(show(lastOpened = 1103, termsApp = 1103))
        // An old install that accepted terms before the key existed.
        assertFalse(show(lastOpened = 1100))
        // Never closed the old welcome tour, but did finish the old artists step.
        assertFalse(show(artistsDone = true))
        // Accepted the terms on beta12 and stopped there.
        assertFalse(show(termsApp = 1111))
        assertFalse(show(termsApp = 1))
        // None of them is stopped by the permissions step either.
        assertFalse(permissionsStep(lastOpened = 1111, termsApp = 1111))
        assertFalse(permissionsStep(artistsDone = true))
        assertFalse(permissionsStep(termsApp = 1111))
    }

    @Test
    fun `a fresh install is shown the welcome once, step by step`() {
        // Nothing stored yet (before the terms): owed.
        assertTrue(show())
        // Its own terms acceptance happens on this build, whose versionCode is above beta12's.
        val thisBuild = WelcomeFlow.LAST_VERSION_CODE_BEFORE_WELCOME + 1
        assertTrue(show(termsApp = thisBuild))
        assertTrue(permissionsStep(termsApp = thisBuild))
        // «Continuar» on the permissions step: that step is over, the rest of the welcome is still owed.
        assertFalse(permissionsStep(permissionsDone = true, termsApp = thisBuild))
        assertTrue(show(termsApp = thisBuild))
        // «Listo»: never again, on this launch or the next.
        assertFalse(show(welcomeDone = true, termsApp = thisBuild))
        assertFalse(permissionsStep(permissionsDone = true, welcomeDone = true, termsApp = thisBuild))
        // And once the welcome tour is closed (LastOpenedVersionCodeKey written), still never.
        assertFalse(show(welcomeDone = true, lastOpened = thisBuild, termsApp = thisBuild))
        // Picking favourite artists later from Ajustes ▸ Contenido does not bring it back.
        assertFalse(show(welcomeDone = true, artistsDone = true, termsApp = thisBuild))
    }

    @Test
    fun `MainActivity reads the same decision from one DataStore snapshot`() {
        assertTrue(WelcomeFlow.permissionsStepPending(emptyPreferences()))
        assertFalse(WelcomeFlow.permissionsStepPending(preferencesOf(LastOpenedVersionCodeKey to 1111)))
        assertFalse(WelcomeFlow.permissionsStepPending(preferencesOf(OnboardingArtistsDoneKey to true)))
        assertFalse(WelcomeFlow.permissionsStepPending(preferencesOf(TermsAcceptedAppVersionKey to 1111)))
        assertTrue(WelcomeFlow.permissionsStepPending(preferencesOf(TermsAcceptedAppVersionKey to 1112)))
        assertFalse(WelcomeFlow.permissionsStepPending(preferencesOf(TermsAcceptedAppVersionKey to 1112, WelcomePermissionsDoneKey to true)))
        assertFalse(WelcomeFlow.permissionsStepPending(preferencesOf(TermsAcceptedAppVersionKey to 1112, WelcomeDoneKey to true)))
    }

    @Test
    fun `the build that ships the welcome has a versionCode above the last build without it`() {
        // A fresh install accepting the terms on a build numbered like beta12 would look like an existing user and
        // skip the whole welcome: the beta that ships it must have bumped versionCode (it is monotonic, AGENTS.md).
        val gradle = File(root, "app/build.gradle.kts").readText()
        val versionCode = Regex("""\bversionCode = (\d+)""").find(gradle)?.groupValues?.get(1)?.toInt() ?: error("versionCode moved")
        assertTrue(
            "versionCode $versionCode must be above ${WelcomeFlow.LAST_VERSION_CODE_BEFORE_WELCOME}",
            versionCode > WelcomeFlow.LAST_VERSION_CODE_BEFORE_WELCOME,
        )
    }

    @Test
    fun `the permissions step asks only for runtime permissions the manifest declares, one card each`() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        WelcomeFlow.Permission.entries.forEach {
            assertTrue("${it.permission} is not declared", manifest.contains("android:name=\"${it.permission}\""))
        }
        assertEquals(
            listOf("NOTIFICATIONS", "MUSIC_FILES", "MICROPHONE", "BLUETOOTH", "LOCAL_NETWORK"),
            WelcomeFlow.permissionsFor(37).map { it.name },
        )
        assertEquals(listOf("NOTIFICATIONS", "MUSIC_FILES", "MICROPHONE", "BLUETOOTH"), WelcomeFlow.permissionsFor(36).map { it.name })
        assertEquals(listOf("MUSIC_FILES_LEGACY", "MICROPHONE", "BLUETOOTH"), WelcomeFlow.permissionsFor(31).map { it.name })
        assertEquals(listOf("MUSIC_FILES_LEGACY", "MICROPHONE"), WelcomeFlow.permissionsFor(30).map { it.name })
    }

    @Test
    fun `the permissions step sits between the terms and the licence, which stay as they were`() {
        val main = File(root, "app/src/main/kotlin/com/music/echo/MainActivity.kt").readText()
        val terms = main.indexOf("iad1tya.echo.music.legal.TermsGate {")
        val permissions = main.indexOf("iad1tya.echo.music.ui.screens.welcome.WelcomePermissionsGate {")
        val licence = main.indexOf("LicenseGate {")
        assertTrue("order: terms → permissions → licence", terms in 0 until permissions && permissions < licence)
        // The bare notification dialog of onStart waits until the step is over on a fresh install.
        assertTrue(main.contains("if (iad1tya.echo.music.ui.screens.welcome.WelcomeFlow.permissionsStepPending(dataStore.data.first())) return@launch"))
        // A denied permission never blocks: «Continuar» has no enabled condition.
        val screen = File(root, "app/src/main/kotlin/com/music/echo/ui/screens/welcome/WelcomePermissionsScreen.kt").readText()
        val cont = screen.substringAfter("ActionLog.tap(ActionId.WELCOME_PERM_CONTINUE)").substringBefore("Text(stringResource(R.string.welcome_continue))")
        assertFalse(cont.contains("enabled ="))
        assertTrue(cont.contains("it[WelcomePermissionsDoneKey] = true"))
    }
}
