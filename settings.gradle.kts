@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        // HALLAZGO-010 (verificado 2026-08-25): el mirror se queda — sin él quedan FAILED
        // ffmpeg-kit-full:6.0-2 y tinypinyin:2.0.3 (solo viven en el espejo de JCenter que sirve
        // aliyun/public). Ver el comentario completo en build.gradle.kts raíz.
        maven { setUrl("https://maven.aliyun.com/repository/public") }
    }
}

// F-Droid doesn't support foojay-resolver plugin
// plugins {
//     id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
// }

rootProject.name = "echomusic"
include(":app")
include(":migration")
include(":canvas")
include(":innertube")
include(":kugou")
include(":lrclib")
include(":betterlyrics")
include(":simpmusic")
include(":youlyplus")
include(":shazamkit")
include(":artistvideo")
include(":applecanvas")
include(":echomusiccanvas")
include(":paxsenixlyrics")
include(":unison")


// Use a local copy of NewPipe Extractor by uncommenting the lines below.
// We assume, that echomusic and NewPipe Extractor have the same parent directory.
// If this is not the case, please change the path in includeBuild().
//
// For this to work you also need to change the implementation in innertube/build.gradle.kts
// to one which does not specify a version.
// From:
//      implementation(libs.newpipe.extractor)
// To:
//      implementation("com.github.teamnewpipe:NewPipeExtractor")
//includeBuild("../NewPipeExtractor") {
//    dependencySubstitution {
//        substitute(module("com.github.teamnewpipe:NewPipeExtractor")).using(project(":extractor"))
//    }
//}
include(":jiosaavn")
