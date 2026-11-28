package me.veilsu.manager.data.repository

import android.annotation.SuppressLint
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.media.MediaScannerConnection
import androidx.core.net.toUri
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import me.veilsu.manager.BuildConfig
import me.veilsu.manager.R
import me.veilsu.manager.ksuApp
import me.veilsu.manager.ui.util.module.LatestVersionInfo
import me.veilsu.manager.ui.util.getRootShell
import me.veilsu.manager.ui.util.shellQuote
import me.veilsu.manager.ui.util.withNewRootShell
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipFile


class CiManagerUpdater(private val context: Context) {
    enum class Stage {
        DOWNLOADING,
        VERIFYING,
        INSTALLING,
    }

    /**
     * [keepApk] false installs straight from the private cache copy and never
     * writes the APK to Downloads, so nothing is left behind after the update.
     *
     * [onProgress] and [onStage] are invoked on the calling (IO) thread. Progress
     * is throttled to a few updates per second; the final callback reports 100%.
     */
    fun updateAndInstall(
        update: LatestVersionInfo,
        keepApk: Boolean = true,
        onStage: (Stage) -> Unit = {},
        onProgress: (downloadedBytes: Long, totalBytes: Long, bytesPerSecond: Long) -> Unit =
            { _, _, _ -> },
    ): Boolean {
        // Custom update sources carry their own release download prefix; the
        // built-in source keeps the original hard-coded one.
        val expectedPrefix = update.downloadUrlPrefix.ifBlank { DEFAULT_RELEASE_ASSET_PREFIX }
        if (!update.downloadUrl.startsWith(expectedPrefix)) {
            throw IOException(context.getString(R.string.ci_update_request_failed))
        }
        val abi = Build.SUPPORTED_ABIS.firstOrNull()?.takeIf { it in SUPPORTED_ABIS }
            ?: throw IOException(context.getString(R.string.ci_update_unsupported_arch))
        val apk = File.createTempFile("manager-ci-", ".apk", context.cacheDir)
        try {
            onStage(Stage.DOWNLOADING)
            download(update.downloadUrl, apk, onProgress)
            onStage(Stage.VERIFYING)
            verifyApk(apk, abi, update.versionCode)
            if (keepApk) {
                saveToDownloads(apk, update.downloadUrl)
            }
            onStage(Stage.INSTALLING)
            if (getRootShell().isRoot) {
                val result = withNewRootShell {
                    if (!isRoot) throw IOException(context.getString(R.string.ci_update_install_failed))
                    // Stream via stdin: system_server cannot open an app-private cache path on SELinux devices.
                    newJob().add("cat ${apk.absolutePath.shellQuote()} | pm install -r -S ${apk.length()}").exec()
                }
                if (!result.isSuccess) {
                    throw IOException((result.err + result.out).joinToString("\n").ifBlank {
                        context.getString(R.string.ci_update_install_failed)
                    })
                }
                return true
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                    throw IOException(context.getString(R.string.ci_update_install_permission))
                }
                submitInstall(apk)
                return false
            }
        } finally {
            apk.delete()
        }
    }

    private fun saveToDownloads(apk: File, url: String) {
        val name = url.toUri().lastPathSegment
            ?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.takeIf { it.endsWith(".apk") } ?: "VeilSU_Nova-update.apk"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Cannot create APK in Downloads")
            try {
                val output = resolver.openOutputStream(uri) ?: throw IOException("Cannot write APK to Downloads")
                output.use { out -> apk.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                if (resolver.update(uri, values, null, null) != 1) throw IOException("Cannot publish APK in Downloads")
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create Downloads directory")
            // Never overwrite another user's download; an incomplete copy is deleted on failure.
            var destination = File(directory, name)
            var index = 1
            while (!destination.createNewFile()) {
                destination = File(directory, "${name.removeSuffix(".apk")} (${index++}).apk")
            }
            try {
                apk.inputStream().use { input -> destination.outputStream().use { input.copyTo(it) } }
            } catch (e: Exception) {
                destination.delete()
                throw e
            }
            MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath),
                arrayOf("application/vnd.android.package-archive"), null)
        }
    }

    private fun download(
        url: String,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long, bytesPerSecond: Long) -> Unit,
    ) {
        val request = Request.Builder().url(url)
        // The Forgejo instance requires auth for every download; without the
        // header the request is 303'd to the login page and the "APK" is an HTML
        // screen. Other hosts (github release mirrors) take no header.
        if (url.startsWith("https://git.zako.shirkneko.top:20004")) {
            request.header("Authorization", "token $RELEASE_DOWNLOAD_TOKEN")
        }
        ksuApp.okhttpClient.newCall(request.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(context.getString(R.string.ci_update_http_error, response.code))
            }
            val body = response.body
            val contentLength = body.contentLength()
            if (contentLength > MAX_APK_BYTES) {
                throw IOException(context.getString(R.string.ci_update_archive_too_large))
            }
            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    val startedAt = SystemClock.elapsedRealtime()
                    var sampledAt = startedAt
                    var sampledBytes = 0L
                    var bytesPerSecond = 0L
                    onProgress(0L, contentLength, 0L)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_APK_BYTES) {
                            throw IOException(context.getString(R.string.ci_update_archive_too_large))
                        }
                        output.write(buffer, 0, count)
                        val now = SystemClock.elapsedRealtime()
                        val elapsed = now - sampledAt
                        if (elapsed >= PROGRESS_SAMPLE_INTERVAL_MS) {
                            bytesPerSecond = (total - sampledBytes) * 1000 / elapsed
                            sampledAt = now
                            sampledBytes = total
                            onProgress(total, contentLength, bytesPerSecond)
                        }
                    }
                    output.fd.sync()
                    if (bytesPerSecond == 0L && total > 0L) {
                        // Small files can finish between samples; report the average.
                        val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1L)
                        bytesPerSecond = total * 1000 / elapsed
                    }
                    onProgress(total, contentLength, bytesPerSecond)
                }
            }
        }
    }

    private fun verifyApk(apk: File, abi: String, expectedVersionCode: Long) {
        val candidate = archivePackageInfo(apk)
            ?: throw IOException(context.getString(R.string.ci_update_invalid_apk))
        if (candidate.packageName != BuildConfig.APPLICATION_ID ||
            candidate.longVersionCodeCompat() != expectedVersionCode ||
            abi !in apkAbis(apk)) {
            throw IOException(context.getString(R.string.ci_update_invalid_apk))
        }
        val installed = try {
            context.packageManager.getPackageInfo(context.packageName, packageInfoFlags())
        } catch (e: Exception) {
            throw IOException(context.getString(R.string.ci_update_invalid_apk), e)
        }
        if (candidate.longVersionCodeCompat() <= installed.longVersionCodeCompat()) {
            throw IOException(context.getString(R.string.ci_update_not_newer))
        }
        if (!sameSignature(candidate, installed)) {
            throw IOException(context.getString(R.string.ci_update_signature_mismatch))
        }
    }

    private fun packageInfoFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
    }

    private fun archivePackageInfo(apk: File): PackageInfo? =
        context.packageManager.getPackageArchiveInfo(apk.absolutePath, packageInfoFlags())

    private fun apkAbis(apk: File): Set<String> = ZipFile(apk).use { zip ->
        val entries = zip.entries()
        buildSet {
            while (entries.hasMoreElements()) {
                val parts = entries.nextElement().name.split('/')
                if (parts.size >= 3 && parts[0] == "lib" && parts[1] in SUPPORTED_ABIS) add(parts[1])
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun sameSignature(candidate: PackageInfo, installed: PackageInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signers = candidate.signingInfo?.apkContentsSigners ?: return false
            return signers.isNotEmpty() && signers.all { signer ->
                context.packageManager.hasSigningCertificate(
                    context.packageName,
                    signer.toByteArray(),
                    PackageManager.CERT_INPUT_RAW_X509,
                )
            }
        }
        val candidateSignatures = candidate.signatures ?: return false
        val installedSignatures = installed.signatures ?: return false
        return candidateSignatures.isNotEmpty() && candidateSignatures.all { signature ->
            installedSignatures.any { it == signature }
        }
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    private fun submitInstall(apk: File) {
        val installer = context.packageManager.packageInstaller
        val sessionId = installer.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
            }
        )
        var committed = false
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("manager.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val callback = Intent(context, CiPackageInstallReceiver::class.java)
                    .setAction(CiPackageInstallReceiver.ACTION_INSTALL_RESULT)
                val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    android.app.PendingIntent.FLAG_MUTABLE
                } else 0
                val pending = android.app.PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callback,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or mutable,
                )
                session.commit(pending.intentSender)
                committed = true
            }
        } finally {
            if (!committed) installer.abandonSession(sessionId)
        }
    }

    companion object {
        private const val DEFAULT_RELEASE_ASSET_PREFIX =
            "https://github.com/tangt875f4b-crypto/VeilSU/releases/download/"
        // Built-in access token for the instance's release downloads (it no
        // longer serves anonymous requests). Read-only scope is enough.
        private const val RELEASE_DOWNLOAD_TOKEN =
            "af757b8a4ea3ac99ac2731aeba8a12743282feb0"
        private const val MAX_APK_BYTES = 512L * 1024 * 1024
        private const val PROGRESS_SAMPLE_INTERVAL_MS = 400L
        private val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "riscv64")
    }
}
