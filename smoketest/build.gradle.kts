// Emulator smoke tests (docs/QA_EMULATOR.md, .github/workflows/emulator-smoke.yml).
//
// A separate test module that drives the debug app from OUTSIDE its process (self-instrumenting, like a
// Macrobenchmark module): UI Automator for the screen, a MediaController for the playback, shell commands for the
// device. It never links against app code and ships nothing — settings.gradle.kts only includes it when a build asks
// for it with -PauraEmulatorSmoke=true, so the app, the release and the unit tests are built exactly as before.
plugins {
    id("com.android.test")
}

android {
    namespace = "iad1tya.echo.music.smoketest"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The app has two flavor dimensions; the emulator is x86_64 and the smoke run builds the FOSS flavor.
        missingDimensionStrategy("abi", "x86_64")
        missingDimensionStrategy("variant", "foss")
    }

    targetProjectPath = ":app"
    // Run in its own process, not inside the app's: the app can be killed, updated (`adb install -r`) and relaunched
    // under the tests, and nothing of the test APK's classpath is loaded next to the app's.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.test:runner:1.7.0")
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.uiautomator:uiautomator:2.4.1")
    // Same version as the app (gradle/libs.versions.toml): the MediaController speaks to the app's MediaSession.
    implementation(libs.media3.session)
}
