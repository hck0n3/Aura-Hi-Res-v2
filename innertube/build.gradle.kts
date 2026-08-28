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
    // Last-resort fallback extractor: maxrave-dev/BravePipeExtractor — the SAME fallback SimpMusic
    // uses when its PipePipe fork fails (Extractor.android.kt in maxrave-dev/core). Fork of TeamNewPipe
    // whose extraction client is ANDROID instead of WEB, so it is not limited to the muxed itag 18 that
    // stock TeamNewPipe v0.25.2 got. Same org.schabi.* package tree as the stock extractor it replaces,
    // so it cannot coexist with com.github.TeamNewPipe:NewPipeExtractor (duplicate classes).
    implementation(libs.bravenewpipeextractor)
    // Diagnostics only. Timber's planted trees are process-global, so a failure logged here reaches the
    // app's AppLogger file tree and therefore the log the USER can send — which is the whole point: the
    // cipher/signature deobfuscation that breaks when YouTube rotates player.js lives in THIS module,
    // and it used to fail with a comment that said "caller handles errors" and no evidence anywhere.
    // Pure logging facade, no Firebase, no transitive Google dependency — safe for the foss flavor.
    implementation(libs.timber)
    testImplementation(libs.junit)

    coreLibraryDesugaring(libs.desugaring)
}
