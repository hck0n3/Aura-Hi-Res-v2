plugins {
    alias(libs.plugins.hilt) apply (false)
    alias(libs.plugins.kotlin.ksp) apply (false)
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.protobufPlugin) apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        // HALLAZGO-010 (verificado 2026-08-25): este mirror NO se puede retirar todavía. La prueba
        // real de resolución sin él deja dos artefactos FAILED: com.arthenica:ffmpeg-kit-full:6.0-2
        // (exportación de audio) y com.github.promeG:tinypinyin:2.0.3 (pinyin de letras) — ambos son
        // dependencias EOL que ya solo existen en el espejo de JCenter que sirve aliyun/public.
        // La salida real es reemplazar esas dos librerías (ffmpeg-kit toca la exportación; tinypinyin
        // es una sola llamada en LyricsUtils); hasta entonces el mirror se queda, al final de la
        // lista para que google()/mavenCentral()/jitpack tengan prioridad de resolución.
        maven { setUrl("https://maven.aliyun.com/repository/public") }
    }
    dependencies {
        classpath(libs.gradle)
        classpath(kotlin("gradle-plugin", libs.versions.kotlin.get()))
        classpath("com.google.gms:google-services:4.4.3")
        classpath("com.google.firebase:firebase-crashlytics-gradle:3.0.2")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// HALLAZGO-011 (2026-08-25): dependency locking. Cada resolución de dependencias queda fijada en
// los gradle.lockfile commiteados: lo que se compiló es exactamente lo que se compilará en CI y en
// cualquier otra máquina, y una sustitución maliciosa o accidental de un artefacto cambia el hash
// y rompe el build en vez de colarse. Actualizar una dependencia exige --write-locks a propósito.
dependencyLocking {
    lockAllConfigurations()
}

subprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
    configurations.all {
        resolutionStrategy {
            // SimpMusic forces this exact nanojson commit in its root build for the same reason:
            // PipePipeExtractor ships an older nanojson without JsonArray.streamAsJsonObjects(), and
            // without the force the BravePipe fallback crashes at runtime with NoSuchMethodError.
            force("com.github.TeamNewPipe:nanojson:c7a6c1c08d16b6d5ecded34758e6415e07be2166")
        }
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            if (project.findProperty("enableComposeCompilerReports") == "true") {
                arrayOf("reports", "metrics").forEach {
                    freeCompilerArgs.add("-P")
                    freeCompilerArgs.add("plugin:androidx.compose.compiler.plugins.kotlin:${it}Destination=${project.layout.buildDirectory}/compose_metrics")
                }
            }
        }
    }
}
