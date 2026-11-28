@file:Suppress("UnstableApiUsage")

import com.google.protobuf.gradle.id
import groovy.json.JsonSlurper
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.OffsetDateTime
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.lsplugin.apksign)
    alias(libs.plugins.protobuf)
    id("kotlin-parcelize")
}

val androidCompileSdkVersion = rootProject.extra["androidCompileSdkVersion"] as Int
val androidCompileSdkVersionMinor = rootProject.extra["androidCompileSdkVersionMinor"] as Int
val androidCompileNdkVersion = rootProject.extra["androidCompileNdkVersion"] as String
val androidBuildToolsVersion = rootProject.extra["androidBuildToolsVersion"] as String
val androidMinSdkVersion = rootProject.extra["androidMinSdkVersion"] as Int
val androidTargetSdkVersion = rootProject.extra["androidTargetSdkVersion"] as Int
val androidSourceCompatibility = rootProject.extra["androidSourceCompatibility"] as JavaVersion
val androidTargetCompatibility = rootProject.extra["androidTargetCompatibility"] as JavaVersion
val managerVersionCode = rootProject.extra["managerVersionCode"] as Int
val managerVersionName = rootProject.extra["managerVersionName"] as String

private data class KpmReleaseAsset(
    val name: String,
    val url: String,
    val size: Long,
)

private data class KpmRelease(
    val tag: String,
    val timestamp: Long,
    val assets: Map<String, KpmReleaseAsset>,
)

/**
 * The rolling `kpm-ci` release is updated in place, so replacing its assets
 * does not move its publication timestamp. Prefer it by tag instead of relying
 * on the timestamp sort; KPM_RELEASE_TAG (env) or -PkpmReleaseTag pins another
 * tag. A pinned/preferred tag that lacks the required assets falls back to the
 * newest complete release with a warning.
 */
private val kpmReleaseTagOverride: String? =
    providers.gradleProperty("kpmReleaseTag").orNull?.takeIf { it.isNotBlank() }
        ?: System.getenv("KPM_RELEASE_TAG")?.takeIf { it.isNotBlank() }

private fun kpmPreferredReleaseTag(): String =
    kpmReleaseTagOverride ?: "kpm-ci"

private fun kpmSelectRelease(
    releases: List<KpmRelease>,
    needs: (KpmRelease) -> Boolean,
): KpmRelease? {
    val preferred = kpmPreferredReleaseTag()
    return releases.firstOrNull { it.tag == preferred && needs(it) }
        ?: releases.firstOrNull(needs)
}

private fun List<KpmRelease>.hasReleaseTag(tag: String): Boolean = any { it.tag == tag }

private fun kpmReleaseApiUrl(): String =
    System.getenv("KPM_RELEASE_API")?.takeIf { it.isNotBlank() }
        ?: "https://git.zako.shirkneko.top:20004/api/v1/repos/shirkneko/SukiSU_KernelPatch_patch/releases"

private fun kpmReleaseToken(): String? =
    System.getenv("KPM_TOOLS_TOKEN")?.takeIf { it.isNotBlank() }
        ?: System.getenv("KPM_RELEASE_TOKEN")?.takeIf { it.isNotBlank() }

private fun kpmConnection(url: String, accept: String): HttpURLConnection {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 10_000
    connection.readTimeout = 30_000
    connection.useCaches = false
    connection.setRequestProperty("User-Agent", "VeilSU-Manager-build")
    connection.setRequestProperty("Accept", accept)
    kpmReleaseToken()?.let { token ->
        if (url.startsWith("https://git.zako.shirkneko.top:20004")) {
            connection.setRequestProperty("Authorization", "token $token")
        }
    }
    return connection
}

private fun kpmReadText(url: String): String {
    val connection = kpmConnection(url, "application/json")
    try {
        check(connection.responseCode == HttpURLConnection.HTTP_OK) {
            "HTTP ${connection.responseCode} for $url"
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

/**
 * The `/releases/latest` endpoint excludes pre-releases, so read the full list.
 * Keep a newest-first order for the fallback; the rolling `kpm-ci` tag is
 * preferred by tag in [kpmSelectRelease] because its timestamp does not move
 * when CI replaces the assets.
 */
private fun kpmFetchReleases(): List<KpmRelease> {
    val parsed = JsonSlurper().parseText(kpmReadText("${kpmReleaseApiUrl()}?limit=50"))
    check(parsed is List<*>) { "KPM release API returned a non-list response" }
    return parsed.mapNotNull { raw ->
        val release = raw as? Map<*, *> ?: return@mapNotNull null
        if (release["draft"] == true) return@mapNotNull null
        val tag = release["tag_name"] as? String ?: return@mapNotNull null
        val published = (release["published_at"] ?: release["created_at"]) as? String ?: ""
        val timestamp = runCatching {
            OffsetDateTime.parse(published).toInstant().toEpochMilli()
        }.getOrDefault(0L)
        val assets = (release["assets"] as? List<*>)
            .orEmpty()
            .mapNotNull { assetRaw ->
                val asset = assetRaw as? Map<*, *> ?: return@mapNotNull null
                val name = asset["name"] as? String ?: return@mapNotNull null
                val url = asset["browser_download_url"] as? String ?: return@mapNotNull null
                val size = (asset["size"] as? Number)?.toLong() ?: 0L
                KpmReleaseAsset(name, url, size)
            }
            .associateBy { it.name }
        KpmRelease(tag, timestamp, assets)
    }.sortedByDescending { it.timestamp }
}

private fun kpmDownload(url: String, target: File): File {
    target.parentFile?.let {
        check(it.isDirectory || it.mkdirs()) { "Cannot create ${it.absolutePath}" }
    }
    target.delete()
    try {
        val connection = kpmConnection(url, "application/octet-stream")
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "HTTP ${connection.responseCode} for $url"
            }
            val expectedSize = connection.contentLengthLong
            connection.inputStream.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            check(target.length() >= 1024 && (expectedSize < 0 || target.length() == expectedSize)) {
                "incomplete download (${target.length()} of $expectedSize bytes) for $url"
            }
        } finally {
            connection.disconnect()
        }
    } catch (networkError: Exception) {
        target.delete()
        val curlCommand = mutableListOf(
            "curl", "--fail", "--location", "--retry", "3", "--silent", "--show-error",
        )
        kpmReleaseToken()?.let { token ->
            if (url.startsWith("https://git.zako.shirkneko.top:20004")) {
                curlCommand += listOf("--header", "Authorization: token $token")
            }
        }
        curlCommand += listOf("--output", target.absolutePath, url)
        val process = ProcessBuilder(*curlCommand.toTypedArray()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0 && target.length() >= 1024) {
            "curl failed for $url: ${output.trim()}"
        }
    }
    return target
}

val isPrBuild = project.findProperty("IS_PR_BUILD")?.toString()?.toBoolean() ?: false
val defaultManagerPackageName = if (isPrBuild) "me.veilsu.manager.pr" else "me.veilsu.manager"
val defaultManagerName = if (isPrBuild) "VeilSU PR" else "VeilSU"
val managerPackageName = project.findProperty("KSU_PACKAGE_NAME")?.toString() ?: defaultManagerPackageName
val managerName = project.findProperty("KSU_NAME")?.toString() ?: defaultManagerName

apksign {
    storeFileProperty = "KEYSTORE_FILE"
    storePasswordProperty = "KEYSTORE_PASSWORD"
    keyAliasProperty = "KEY_ALIAS"
    keyPasswordProperty = "KEY_PASSWORD"
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        ofNonTest().forEach { task ->
            task.builtins {
                id("java") {
                    option("lite")
                }
                id("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

val baseCFlags = listOf(
    "-Wall", "-Qunused-arguments", "-fvisibility=hidden", "-fvisibility-inlines-hidden",
    "-fno-exceptions", "-fno-stack-protector", "-fomit-frame-pointer",
    "-Wno-builtin-macro-redefined", "-Wno-unused-value", "-D__FILE__=__FILE_NAME__"
)
val baseCppFlags = baseCFlags + "-fno-rtti"

android {
    namespace = "me.veilsu.manager"

    buildTypes {
        debug {
            externalNativeBuild {
                cmake {
                    arguments += listOf("-DCMAKE_CXX_FLAGS_DEBUG=-Og", "-DCMAKE_C_FLAGS_DEBUG=-Og")
                }
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            externalNativeBuild {
                cmake {
                    arguments += "-DDEBUG_SYMBOLS_PATH=${layout.buildDirectory.get().asFile.absolutePath}/symbols"
                    arguments += "-DCMAKE_BUILD_TYPE=Release"

                    val releaseFlags = listOf(
                        "-flto", "-ffunction-sections", "-fdata-sections", "-Wl,--gc-sections",
                        "-fno-unwind-tables", "-fno-asynchronous-unwind-tables", "-Wl,--exclude-libs,ALL"
                    )
                    val configFlags = listOf("-Oz", "-DNDEBUG").joinToString(" ")

                    cppFlags += releaseFlags
                    cFlags += releaseFlags

                    arguments += listOf(
                        "-DCMAKE_CXX_FLAGS_RELEASE=$configFlags",
                        "-DCMAKE_C_FLAGS_RELEASE=$configFlags",
                        "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,--icf=all -s -Wl,--hash-style=sysv -Wl,-z,norelro"
                    )
                }
            }
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
        resValues = true
        compose = true
        prefab = true
    }

    packaging {
        dex {
            useLegacyPackaging = true
        }
        jniLibs {
            useLegacyPackaging = true
            excludes += "lib/*/libandroidx.graphics.path.so"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    androidResources {
        generateLocaleConfig = true
    }
    compileSdk {
        version =
            release(androidCompileSdkVersion) {
                minorApiLevel = androidCompileSdkVersionMinor
            }
    }
    buildToolsVersion = androidBuildToolsVersion
    ndkVersion = androidCompileNdkVersion

    defaultConfig {
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        versionCode = managerVersionCode
        versionName = managerVersionName
        applicationId = managerPackageName

        buildConfigField("boolean", "IS_PR_BUILD", isPrBuild.toString())
        val commit = providers.exec { commandLine("git", "rev-parse", "HEAD") }.standardOutput.asText.get().trim()
        buildConfigField("String", "MANAGER_COMMIT", "\"$commit\"")
        resValue("string", "app_name", managerName)

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=none"
                cFlags += baseCFlags + "-std=c2x"
                cppFlags += baseCppFlags + "-std=c++2b"
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "riscv64")
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = androidSourceCompatibility
        targetCompatibility = androidTargetCompatibility
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) {
        it.packaging.resources.excludes.addAll(listOf(
            "META-INF/**",
            "kotlin/**",
            "**.bin",
            // Strip protobuf source files bundled by the protobuf runtime jar
            "google/**",
            "src/**",
        ))
    }
}

base {
    archivesName.set(
        "${managerName.replace(" ", "_")}_${managerVersionName}_${managerVersionCode}"
    )
}

dependencies {
    // java.time and java.nio.file are used throughout the manager but only exist
    // from Android 8.0; desugaring keeps them available on Android 7.
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    // Stable bridge shared with the optional assistant AAR.
    implementation(project(":ai-api"))
    implementation(libs.androidx.exifinterface)

    // The assistant itself is optional: a drop-in app/libs/ai.aar wins,
    // otherwise the :ai module is used when it is part of the build. Without
    // either, the host compiles and AiFeatureBridge hides every AI entry.
    val aiAar = file("libs/ai.aar")
    val aiProject = findProject(":ai")
    if (aiAar.isFile) {
        implementation(files(aiAar))
    } else if (aiProject != null) {
        implementation(project(":ai"))
    }

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.core)
    implementation(files("libs/material-icons-custom.jar"))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.documentfile)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigationevent.compose)

    implementation(libs.com.github.topjohnwu.libsu.core)
    implementation(libs.com.github.topjohnwu.libsu.service)
    implementation(libs.com.github.topjohnwu.libsu.io)

    implementation(libs.dev.rikka.rikkax.parcelablelist)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.commonmark.ext.task.list.items)

    implementation(libs.androidx.webkit)

    implementation(libs.lsposed.cxx)

    implementation(libs.hiddenapibypass)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.nav)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)

    implementation(libs.reorderable)

    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    implementation(libs.material.kolor)

    implementation(libs.appiconloader)

    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.protobuf.kotlin.lite)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

// Resolved lazily at execution time; using AGP's providers also honours local.properties.
val androidSdkDirectory = androidComponents.sdkComponents.sdkDirectory
val androidNdkDirectory = androidComponents.sdkComponents.ndkDirectory
val kpmToolsDir = layout.buildDirectory.dir("generated/kpmTools")
val downloadKpmTools = tasks.register("downloadKpmTools") {
    description = "Download the latest KPM tools to bundle in the APK"
    outputs.dir(kpmToolsDir)
    // Keep the downloaded tools in the build directory. Once both files are
    // present, subsequent builds use the local copies without contacting GitHub.
    outputs.upToDateWhen {
        val root = kpmToolsDir.get().asFile
        root.resolve("jniLibs/arm64-v8a/libkptools.so").isFile &&
            root.resolve("assets/kpm/kpimg").isFile
    }
    doLast {
        val root = kpmToolsDir.get().asFile
        val kptoolsOutput = root.resolve("jniLibs/arm64-v8a/libkptools.so")
        val kpimgOutput = root.resolve("assets/kpm/kpimg")

        // A pre-seeded copy wins: CI bakes the two files and copies them in after
        // `gradle clean`, because none of the sources below is reachable from every
        // builder. upToDateWhen only helps within a warm build directory, so check
        // the files here as well.
        if (kptoolsOutput.isFile && kpimgOutput.isFile &&
            kptoolsOutput.length() > 1024 && kpimgOutput.length() > 1024
        ) {
            logger.lifecycle("Using existing KPM tools in $root")
            return@doLast
        }

        // First entry wins: KPM_TOOLS_BASE_URL lets CI point at its own mirror, the
        // second is the release assets of the KernelPatch mirror, and the last is
        // upstream (raw.githubusercontent.com has no idea about repository
        // renames, so the old ShirkNeko path would 404 there).
        val baseUrls = buildList {
            System.getenv("KPM_TOOLS_BASE_URL")?.takeIf { it.isNotBlank() }?.let(::add)
            // Bump the version here when the bundled KPM tools are updated.
            add("https://github.com/ShirkNeko/SukiSU_KernelPatch_patch/releases/download/0.14.0")
            add("https://raw.githubusercontent.com/SukiSU-Nova/SukiSU_patch/main/kpm")
        }.distinct()

        fun download(name: String, baseUrl: String): File {
            val target = temporaryDir.resolve(name)
            target.delete()
            val url = "$baseUrl/$name"
            try {
                val connection = URI(url).toURL().openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 20_000
                    connection.useCaches = false
                    connection.setRequestProperty("User-Agent", "VeilSU-Manager-build")
                    check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                        "HTTP ${connection.responseCode}"
                    }
                    val expectedSize = connection.contentLengthLong
                    connection.inputStream.use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    }
                    check(target.length() >= 1024 && (expectedSize < 0 || target.length() == expectedSize)) {
                        "incomplete download (${target.length()} of $expectedSize bytes)"
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (networkError: Exception) {
                logger.warn("Java HTTPS download failed for $url (${networkError.message}); retrying with curl")
                val process = ProcessBuilder(
                    "curl", "--fail", "--location", "--retry", "3", "--silent", "--show-error",
                    "--output", target.absolutePath, url,
                ).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                check(process.waitFor() == 0 && target.length() >= 1024) {
                    "curl failed for $url: ${output.trim()}"
                }
            }
            return target
        }

        // Try each mirror in order; only the first one that yields both files wins.
        val sources = LinkedHashMap<String, File>()
        var lastError: Exception? = null
        for (baseUrl in baseUrls) {
            try {
                val kptools = download("kptools-android", baseUrl)
                val kpimg = download("kpimg", baseUrl)
                sources["kptools-android"] = kptools
                sources["kpimg"] = kpimg
                logger.lifecycle("Using KPM tools from $baseUrl")
                break
            } catch (e: Exception) {
                lastError = e
                logger.warn("KPM tools not available from $baseUrl: ${e.message}")
            }
        }
        check(sources.size == 2) {
            "Cannot download the KPM tools from any mirror: ${lastError?.message}"
        }

        // Publish only after both downloads have passed validation.
        sources.forEach { (name, source) ->
            val relativePath =
                if (name == "kptools-android") "jniLibs/arm64-v8a/libkptools.so" else "assets/kpm/kpimg"
            val output = root.resolve(relativePath)
            check(output.parentFile.isDirectory || output.parentFile.mkdirs()) { "Cannot create $output" }
            source.copyTo(output, overwrite = true)
            if (name == "kptools-android") {
                // ELF magic plus e_machine: catching a wrong-architecture asset here is
                // much cheaper than finding out on a device.
                val header = output.inputStream().use { it.readNBytes(20) }
                check(
                    header.size >= 20 &&
                        header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
                        header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte(),
                ) { "Downloaded kptools-android is not an ELF file" }
                val machine = (header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8)
                check(machine == 0xb7) {
                    "Downloaded kptools-android is not AArch64 (e_machine=0x${machine.toString(16)})"
                }

                val isWindows = System.getProperty("os.name").lowercase().contains("windows")
                val stripName = if (isWindows) "llvm-strip.exe" else "llvm-strip"
                // Prefer AGP's NDK location, then the SDK/NDK environment variables. Never build a
                // File from an empty string: an unset SDK simply means stripping is skipped.
                val ndkCandidates = buildList {
                    androidNdkDirectory.orNull?.asFile?.let(::add)
                    val sdkDir = androidSdkDirectory.orNull?.asFile
                        ?: System.getenv("ANDROID_HOME")?.takeIf { it.isNotBlank() }?.let { file(it) }
                        ?: System.getenv("ANDROID_SDK_ROOT")?.takeIf { it.isNotBlank() }?.let { file(it) }
                    if (sdkDir != null) {
                        add(sdkDir.resolve("ndk/$androidCompileNdkVersion"))
                        sdkDir.resolve("ndk").listFiles()?.filter { it.isDirectory }
                            ?.sortedByDescending { it.name }?.forEach(::add)
                        add(sdkDir.resolve("ndk-bundle"))
                    }
                    System.getenv("ANDROID_NDK_HOME")?.takeIf { it.isNotBlank() }?.let { file(it) }?.let(::add)
                }
                val stripExe = ndkCandidates.asSequence()
                    .filter { it.isDirectory }
                    .flatMap { ndk ->
                        val prebuilt = ndk.resolve("toolchains/llvm/prebuilt")
                        (prebuilt.listFiles() ?: emptyArray()).asSequence()
                            .filter { it.isDirectory }
                            .mapNotNull { it.resolve("bin/$stripName").takeIf { bin -> bin.isFile } }
                    }
                    .firstOrNull()
                // A pre-seeded copy is already stripped, and stripping twice is harmless
                // anyway, so this runs for both paths.
                val cmd = if (stripExe != null) {
                    listOf(stripExe.absolutePath, "--strip-all", output.absolutePath)
                } else {
                    listOf(stripName, "--strip-all", output.absolutePath)
                }
                runCatching {
                    val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
                    val procOutput = proc.inputStream.bufferedReader().use { it.readText() }
                    if (proc.waitFor() == 0) {
                        logger.lifecycle("Stripped $name: ${output.length()} bytes")
                    } else {
                        logger.warn("Strip failed on $name: $procOutput")
                    }
                }.onFailure {
                    logger.warn("Could not execute strip on $name", it)
                }
            }
        }
    }
}

// Bundle the per-KMI kpm.ko files from the newest release that actually
// carries them.  KPM_MODULE_DIR remains an explicit local-development override.
val kpmKmiNames = listOf(
    "android12-5.10", "android13-5.10", "android13-5.15", "android14-5.15",
    "android14-6.1", "android15-6.6", "android16-6.12", "android17-6.18",
)
val kpmModulesDir = layout.buildDirectory.dir("generated/kpmModules")
val bundleKpmModules = tasks.register("bundleKpmModules") {
    description = "Download the latest per-KMI kpm.ko files as APK assets"
    outputs.dir(kpmModulesDir)
    // The rolling release can change without any Gradle input changing.
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    doLast {
        val root = kpmModulesDir.get().asFile
        val outputDir = root.resolve("assets/kpm/modules")
        outputDir.deleteRecursively()
        check(outputDir.isDirectory || outputDir.mkdirs()) { "Cannot create $outputDir" }

        fun validateKo(file: File) {
            check(file.length() >= 1024) { "${file.name} is too small to be a kernel module" }
            val header = file.inputStream().use { it.readNBytes(64) }
            check(header.size == 64) { "${file.name} is truncated" }
            check(
                header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
                    header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte(),
            ) { "${file.name} is not an ELF file" }
            check(header[4] == 2.toByte()) { "${file.name} is not 64-bit" }
            check(header[5] == 1.toByte()) { "${file.name} is not little-endian" }
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            check(buffer.getShort(16).toInt() == 1) { "${file.name} is not ET_REL" }
            check(buffer.getShort(18).toInt() == 0xB7) { "${file.name} is not AArch64" }
        }

        fun copyLocal(sourceDir: File, reason: String): Boolean {
            if (!sourceDir.isDirectory) return false
            kpmKmiNames.forEach { kmi ->
                val source = sourceDir.resolve("kpm-$kmi.ko")
                if (!source.isFile) {
                    logger.warn("Missing $source; no bundled kpm.ko for $kmi")
                    return@forEach
                }
                validateKo(source)
                source.copyTo(outputDir.resolve(source.name), overwrite = true)
            }
            val bundled = outputDir.listFiles()
                ?.count { it.name.startsWith("kpm-") && it.extension == "ko" } ?: 0
            if (bundled == 0) {
                logger.warn("No kpm.ko files found in $reason")
                return false
            }
            logger.lifecycle("Bundled $bundled kpm.ko file(s) from $reason")
            return true
        }

        val configured = providers.gradleProperty("KPM_MODULE_DIR").orNull
            ?: providers.environmentVariable("KPM_MODULE_DIR").orNull
        if (!configured.isNullOrBlank()) {
            val sourceDir = file(configured)
            if (copyLocal(sourceDir, sourceDir.absolutePath)) return@doLast
            logger.warn("KPM_MODULE_DIR is set but has no kpm.ko files: $sourceDir")
            return@doLast
        }

        val releases = try {
            kpmFetchReleases()
        } catch (e: Exception) {
            // VeilSU: the Forgejo release API is not a build dependency —
            // if it is unreachable, skip bundled kpm.ko files instead of
            // failing the build; KPM install then needs a user-selected .ko.
            logger.warn("KPM release API unreachable (${e.message}); skipping bundled kpm.ko files")
            return@doLast
        }
        val latest = releases.firstOrNull()
        val preferredTag = kpmPreferredReleaseTag()
        val release = kpmSelectRelease(releases) { candidate ->
            candidate.assets.keys.any { it.startsWith("kpm-") && it.endsWith(".ko") } ||
                candidate.assets.containsKey("kpm-modules.zip")
        }
        if (release != null) {
            when {
                release.tag == preferredTag ->
                    logger.lifecycle("Using KPM modules from preferred release $preferredTag")
                releases.hasReleaseTag(preferredTag) ->
                    logger.warn(
                        "KPM release $preferredTag has no kpm.ko assets; " +
                            "using ${release.tag}",
                    )
                latest != null && latest.tag != release.tag ->
                    logger.warn(
                        "Latest KPM release ${latest.tag} has no kpm.ko assets; " +
                            "using the newest release that does: ${release.tag}",
                    )
                else -> logger.lifecycle("Using KPM modules from release ${release.tag}")
            }
            val expectedNames = kpmKmiNames.map { "kpm-$it.ko" }
            val temp = temporaryDir
            expectedNames.forEach { name ->
                val asset = release.assets[name] ?: return@forEach
                val downloaded = kpmDownload(asset.url, temp.resolve(name))
                validateKo(downloaded)
                downloaded.copyTo(outputDir.resolve(name), overwrite = true)
            }
            val zip = release.assets["kpm-modules.zip"]
            if (zip != null && expectedNames.any { !outputDir.resolve(it).isFile }) {
                val downloaded = kpmDownload(zip.url, temp.resolve("kpm-modules.zip"))
                ZipFile(downloaded).use { archive ->
                    val entries = archive.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        val name = File(entry.name).name
                        if (name !in expectedNames) continue
                        archive.getInputStream(entry).use { input ->
                            outputDir.resolve(name).outputStream().use { input.copyTo(it) }
                        }
                    }
                }
                expectedNames.forEach { name ->
                    val file = outputDir.resolve(name)
                    if (file.isFile) validateKo(file) else logger.warn("Missing $name in ${zip.name}")
                }
            }
            val bundled = outputDir.listFiles()
                ?.count { it.name.startsWith("kpm-") && it.extension == "ko" } ?: 0
            logger.lifecycle("Bundled $bundled kpm.ko file(s) from release ${release.tag}")
            return@doLast
        }

        // A fresh release may not have module assets yet; keep local builds
        // useful, but make the fallback explicit in the log.
        val defaultCandidates = listOf(
            rootProject.projectDir.resolve("../SukiSU_KernelPatch_patch/kpm-lkm/out"),
            rootProject.projectDir.resolve("../../SukiSU_KernelPatch_patch/kpm-lkm/out"),
            rootProject.projectDir.parentFile.resolve("SukiSU_KernelPatch_patch/kpm-lkm/out"),
        )
        val fallback = defaultCandidates.firstOrNull { it.isDirectory }
        if (fallback != null && copyLocal(fallback, "local fallback ${fallback.absolutePath}")) {
            logger.warn(
                "No KPM release has kpm.ko assets yet; using local fallback. " +
                    "Upload them to the release to make the APK release-backed.",
            )
            return@doLast
        }
        logger.warn("No kpm.ko assets in the latest release and no local fallback; bundled KPM module install will require a user-selected .ko")
    }
}
android.sourceSets.getByName("main").apply {
    // The generated directories are populated by downloadKpmTools/bundleKpmModules before preBuild.
    // Register them directly to avoid the deprecated srcDir(Any) overload.
    assets.directories.add(kpmToolsDir.get().dir("assets").asFile.toString())
    assets.directories.add(kpmModulesDir.get().dir("assets").asFile.toString())
    // Native libraries are extracted to an executable location by Android.
    jniLibs.directories.add(kpmToolsDir.get().dir("jniLibs").asFile.toString())
}
tasks.named("preBuild").configure {
    dependsOn(downloadKpmTools)
    dependsOn(bundleKpmModules)
}

// NOTE (2026-11-23): the AAR metadata check is disabled for this module only.
// 24 dependencies (miuix 0.9.4, material-kolor 5.0.1, lifecycle 2.11.0,
// okhttp 5.5.0, Compose 1.12.0 / material3 1.5.0-alpha22 pulled in by them)
// declare minCompileSdk 37, and miuix has NO published version that allows
// 36, so the check can never pass while compiling against SDK 36 — and
// platforms;android-37 is not distributed through any CI-reachable channel
// (stable repo, sdkmanager --channel=3, Android CLI --canary, runner image
// all report the package does not exist; verified in CI runs #6/#11/#12).
// Compiling against 36 is still safe for the v1 device test: the check only
// guards against libraries calling API-37-only methods, and the test device
// runs Android 17 (API 37), where those methods exist. Re-enable this check
// once a real android-37 platform is available in CI.
// (tasks.named(...) does NOT work here: AGP registers checkReleaseAarMetadata
// lazily, so the lookup fails at configuration time — run #16. configureEach
// applies to tasks registered later as well.)
tasks.configureEach {
    if (name == "checkReleaseAarMetadata") {
        enabled = false
    }
}

// Same as :ai-api (run #18): the disabled check never creates its output
// directory. Pre-create it at configuration time in case any later task
// validates it as an input.
file("build/intermediates/aar_metadata_check/release/checkReleaseAarMetadata").mkdirs()
