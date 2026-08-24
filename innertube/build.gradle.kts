plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.music.innertube"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)
    implementation(libs.brotli)
    // SimpMusic's own extractor fork (maxrave-dev/PipePipeExtractor). Its extraction client is
    // ANDROID_VR, which is NOT burned by YouTube's bot-check, so it returns full adaptive
    // video/audio formats where stock TeamNewPipe only yields muxed itag 18 (360p). protobuf-java
    // is excluded because the app module already ships protobuf-javalite (duplicate classes).
    implementation(libs.pipepipeextractor) {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    // Last-resort fallback extractor (stock TeamNewPipe v0.25.2, www.youtube.com anonymous). Bot-limited
    // to muxed itag 18, but that itag 18 is exactly what still played on 2026-08-23 when PipePipe's
    // extraction was blocked (anti-bot "sign in" wall on anonymous, "not valid" with cookie). Different
    // package tree (org.schabi.*) so it coexists with the fork.
    implementation(libs.newpipeextractor)
    // Diagnostics only. Timber's planted trees are process-global, so a failure logged here reaches the
    // app's AppLogger file tree and therefore the log the USER can send — which is the whole point: the
    // cipher/signature deobfuscation that breaks when YouTube rotates player.js lives in THIS module,
    // and it used to fail with a comment that said "caller handles errors" and no evidence anywhere.
    // Pure logging facade, no Firebase, no transitive Google dependency — safe for the foss flavor.
    implementation(libs.timber)
    testImplementation(libs.junit)

    coreLibraryDesugaring(libs.desugaring)
}
