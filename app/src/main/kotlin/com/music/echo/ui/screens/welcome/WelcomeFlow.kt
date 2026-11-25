package iad1tya.echo.music.ui.screens.welcome

import androidx.datastore.preferences.core.Preferences
import iad1tya.echo.music.constants.LastOpenedVersionCodeKey
import iad1tya.echo.music.constants.OnboardingArtistsDoneKey
import iad1tya.echo.music.constants.TermsAcceptedAppVersionKey
import iad1tya.echo.music.constants.WelcomeDoneKey
import iad1tya.echo.music.constants.WelcomePermissionsDoneKey

/**
 * Registry rows 871/872 (plan phase 2): who sees the new welcome — Terms → Permissions → Licence/demo → «Conecta tu
 * música» → Listo — and which of its steps is still ahead.
 *
 * Users who installed before it (beta12 and earlier) must NEVER see it, and nothing is written to tell them apart: an
 * install that predates the welcome already carries one of three keys only an earlier build could have written.
 *  · `LastOpenedVersionCodeKey` ≠ -1: the welcome tour was closed once, or the app was opened after an update (the
 *    same "first run ever" test MainActivity has always used).
 *  · `OnboardingArtistsDoneKey`: the old onboarding's artist step was finished.
 *  · `TermsAcceptedAppVersionKey` between 1 and [LAST_VERSION_CODE_BEFORE_WELCOME]: the terms were accepted on a build
 *    that had no new welcome. A fresh install accepts them on this build (a higher versionCode, which only grows), so its
 *    own acceptance — written just before the permissions step — never makes it look like an existing user.
 * Pure functions of the stored values, so the decision is the same on every launch until the welcome is finished.
 */
object WelcomeFlow {
    /** versionCode of 2.0.69-beta12 (app/build.gradle.kts), the last published build without the new welcome. */
    const val LAST_VERSION_CODE_BEFORE_WELCOME = 1111

    /** MainActivity's "never opened" value of [LastOpenedVersionCodeKey]. */
    const val NEVER_OPENED = -1

    fun isExistingUser(
        lastOpenedVersionCode: Int,
        onboardingArtistsDone: Boolean,
        termsAcceptedAppVersionCode: Int,
    ): Boolean =
        lastOpenedVersionCode != NEVER_OPENED ||
            onboardingArtistsDone ||
            termsAcceptedAppVersionCode in 1..LAST_VERSION_CODE_BEFORE_WELCOME

    /** The welcome is still owed: a fresh install that has not reached «Listo». */
    fun showWelcome(
        welcomeDone: Boolean,
        lastOpenedVersionCode: Int,
        onboardingArtistsDone: Boolean,
        termsAcceptedAppVersionCode: Int,
    ): Boolean = !welcomeDone && !isExistingUser(lastOpenedVersionCode, onboardingArtistsDone, termsAcceptedAppVersionCode)

    /** The permissions step (between the terms and the licence) is still ahead. */
    fun showPermissionsStep(
        permissionsDone: Boolean,
        welcomeDone: Boolean,
        lastOpenedVersionCode: Int,
        onboardingArtistsDone: Boolean,
        termsAcceptedAppVersionCode: Int,
    ): Boolean = !permissionsDone &&
        showWelcome(welcomeDone, lastOpenedVersionCode, onboardingArtistsDone, termsAcceptedAppVersionCode)

    /** [showPermissionsStep] read from one DataStore snapshot (MainActivity.onStart). */
    fun permissionsStepPending(prefs: Preferences): Boolean = showPermissionsStep(
        permissionsDone = prefs[WelcomePermissionsDoneKey] ?: false,
        welcomeDone = prefs[WelcomeDoneKey] ?: false,
        lastOpenedVersionCode = prefs[LastOpenedVersionCodeKey] ?: NEVER_OPENED,
        onboardingArtistsDone = prefs[OnboardingArtistsDoneKey] ?: false,
        termsAcceptedAppVersionCode = prefs[TermsAcceptedAppVersionKey] ?: 0,
    )

    /**
     * One runtime permission the welcome asks for, each with its own card. [permission] is the manifest name (all of
     * them are declared in AndroidManifest.xml); [minSdk] is the first Android release where it is a runtime permission
     * Aura must ask for (below it the system grants it at install, or it does not exist).
     */
    enum class Permission(val permission: String, val minSdk: Int, val maxSdk: Int = Int.MAX_VALUE) {
        NOTIFICATIONS("android.permission.POST_NOTIFICATIONS", minSdk = 33),
        MUSIC_FILES("android.permission.READ_MEDIA_AUDIO", minSdk = 33),
        MUSIC_FILES_LEGACY("android.permission.READ_EXTERNAL_STORAGE", minSdk = 1, maxSdk = 32),
        MICROPHONE("android.permission.RECORD_AUDIO", minSdk = 23),
        BLUETOOTH("android.permission.BLUETOOTH_CONNECT", minSdk = 31),
        LOCAL_NETWORK("android.permission.ACCESS_LOCAL_NETWORK", minSdk = 37),
    }

    /** The cards shown on this Android release, in the order they are listed. */
    fun permissionsFor(sdkInt: Int): List<Permission> =
        Permission.entries.filter { sdkInt >= it.minSdk && sdkInt <= it.maxSdk }
}
