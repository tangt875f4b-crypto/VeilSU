@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.agp.library)
    alias(libs.plugins.compose.compiler)
}

val androidCompileSdkVersion = rootProject.extra["androidCompileSdkVersion"] as Int
val androidCompileSdkVersionMinor = rootProject.extra["androidCompileSdkVersionMinor"] as Int
val androidBuildToolsVersion = rootProject.extra["androidBuildToolsVersion"] as String
val androidMinSdkVersion = rootProject.extra["androidMinSdkVersion"] as Int
val androidSourceCompatibility = rootProject.extra["androidSourceCompatibility"] as JavaVersion
val androidTargetCompatibility = rootProject.extra["androidTargetCompatibility"] as JavaVersion

android {
    namespace = "me.veilsu.manager.ai.api"

    compileSdk {
        version =
            release(androidCompileSdkVersion) {
                minorApiLevel = androidCompileSdkVersionMinor
            }
    }
    buildToolsVersion = androidBuildToolsVersion

    defaultConfig {
        minSdk = androidMinSdkVersion
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = androidSourceCompatibility
        targetCompatibility = androidTargetCompatibility
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}

// NOTE (2026-11-23): same as :app — the AAR metadata check is disabled for
// this module. With the upstream Compose BOM 2026.09.00 (required by the app
// code, see libs.versions.toml) the Compose deps declare minCompileSdk 37,
// which CI cannot provide (platforms;android-37 is not distributed). The
// :app module's check is disabled the same way; see its build file.
// (tasks.named(...) does NOT work: AGP registers checkReleaseAarMetadata
// lazily — run #16. configureEach also applies to tasks registered later.)
tasks.configureEach {
    if (name == "checkReleaseAarMetadata") {
        enabled = false
    }
}

// The disabled check never creates its output directory, but Gradle
// validates :ai-api:bundleReleaseAar's `aarMetadataCheck` input at execution
// time and fails the build if the directory does not exist (run #18).
// Pre-create it at configuration time; it stays empty and nothing consumes it.
file("build/intermediates/aar_metadata_check/release/checkReleaseAarMetadata").mkdirs()
