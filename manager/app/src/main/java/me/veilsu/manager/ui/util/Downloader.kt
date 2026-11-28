package me.veilsu.manager.ui.util

import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.veilsu.manager.data.registry.ExpectedFile
import me.veilsu.manager.data.repository.SettingsRepositoryImpl
import me.veilsu.manager.data.update.CnbReleaseParser
import me.veilsu.manager.data.update.SourceReleaseAsset
import me.veilsu.manager.data.update.UpdateSourceConfig
import me.veilsu.manager.data.update.UpdateSourceDefaults
import me.veilsu.manager.data.update.UpdateSourcePlatform
import me.veilsu.manager.ksuApp
import me.veilsu.manager.ui.util.module.LatestVersionInfo
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * @author weishu
 * @date 2023/6/22.
 */
suspend fun download(
    url: String,
    fileName: String,
    onDownloaded: (Uri) -> Unit = {},
    onDownloading: () -> Unit = {},
    onProgress: (Int) -> Unit = {},
    expected: ExpectedFile? = null,
    onFailed: (DownloadManager.DownloadState) -> Unit = {},
) {
    onDownloading()

    val downloadId = DownloadManager.enqueue(
        context = ksuApp,
        url = url,
        fileName = fileName,
        onCompleted = onDownloaded,
        expected = expected,
    )

    val finalState = DownloadManager.downloads
        .onEach { map -> map[downloadId]?.let { onProgress(it.progress) } }
        .first { map ->
            val status = map[downloadId]?.status
            status == DownloadManager.Status.COMPLETED ||
                status == DownloadManager.Status.FAILED
        }[downloadId]
    if (finalState?.status == DownloadManager.Status.FAILED) {
        withContext(Dispatchers.Main) { onFailed(finalState) }
    }
}

internal suspend fun isDownloadAvailable(uri: Uri): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        ksuApp.contentResolver.openFileDescriptor(uri, "r").use { it != null }
    }.getOrDefault(false)
}


suspend fun checkNewVersion(): LatestVersionInfo = checkReleaseVersion(isCi = false)

suspend fun checkNewCiVersion(): LatestVersionInfo = checkReleaseVersion(isCi = true)

private suspend fun checkReleaseVersion(isCi: Boolean): LatestVersionInfo {
    val settings = SettingsRepositoryImpl()
    val sources = buildList {
        addAll(UpdateSourceDefaults.BUILTIN_SOURCES)
        addAll(settings.updateSources.filter { it.enabled })
    }
    val pinnedId = settings.pinnedUpdateSourceId
    val pinned = sources.firstOrNull { it.id == pinnedId }
        ?: if (pinnedId.isNotBlank()) {
            sources.firstOrNull { it.id == UpdateSourceDefaults.DEFAULT_ID }
        } else {
            null
        }

    if (pinned != null) {
        querySource(pinned, isCi)?.let { return it.info }
    }

    val candidates = sources.filter { pinned == null || it.id != pinned.id }
    if (candidates.isEmpty()) return LatestVersionInfo()
    return raceSources(candidates, isCi)
}

/**
 * Queries every candidate in parallel and returns the first successful answer.
 * Losing requests are cancelled so one dead mirror cannot delay the update
 * prompt until the HTTP timeout; since all requests start together, the first
 * success is also the lowest-latency source.
 */
private suspend fun raceSources(
    candidates: List<UpdateSourceConfig>,
    isCi: Boolean,
): LatestVersionInfo = coroutineScope {
    val results = Channel<TimedVersionInfo>(Channel.UNLIMITED)
    val queries = candidates.map { source ->
        launch(Dispatchers.IO) {
            querySource(source, isCi)?.let { results.trySend(it) }
        }
    }
    val closer = launch {
        queries.joinAll()
        results.close()
    }
    val fastest = try {
        results.receiveCatching().getOrNull()
    } finally {
        queries.forEach { it.cancel() }
        closer.cancel()
        results.close()
    }
    fastest?.let {
        android.util.Log.d(
            "UpdateCheck",
            "using ${it.info.sourceName.ifBlank { it.info.sourceId }} (${it.elapsedMs} ms)",
        )
    }
    fastest?.info ?: LatestVersionInfo()
}

private data class TimedVersionInfo(
    val info: LatestVersionInfo,
    val elapsedMs: Long,
)

private suspend fun querySource(
    source: UpdateSourceConfig,
    isCi: Boolean,
): TimedVersionInfo? {
    val started = System.nanoTime()
    return try {
        val info = fetchFromSource(source, isCi)
        info.takeIf { it.versionCode > 0L && it.downloadUrl.isNotBlank() }?.let {
            TimedVersionInfo(it, (System.nanoTime() - started) / 1_000_000)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        android.util.Log.w("UpdateCheck", "${source.displayName}: update check failed", error)
        null
    }
}

private val updateHttpClient by lazy {
    ksuApp.okhttpClient.newBuilder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
}

private suspend fun fetchFromSource(
    source: UpdateSourceConfig,
    isCi: Boolean,
): LatestVersionInfo {
    val endpoint = if (isCi) {
        "tags/${UpdateSourceDefaults.CI_TAG}"
    } else {
        UpdateSourceDefaults.STABLE_ENDPOINT
    }
    val request = Request.Builder()
        .url(source.releasesApiUrl(endpoint))
        .header("Accept", source.acceptHeader)
        .build()

    val response = fetchUpdateBody(source, request) ?: return LatestVersionInfo()
    if (!response.successful) {
        android.util.Log.w("UpdateCheck", "${source.displayName}: HTTP ${response.code}")
        return LatestVersionInfo()
    }
    return try {
        parseReleaseResponse(source, isCi, response.text)
    } catch (error: Exception) {
        android.util.Log.w(
            "UpdateCheck",
            "${source.displayName}: cannot parse release response",
            error,
        )
        LatestVersionInfo()
    }
}

private data class UpdateResponseBody(
    val successful: Boolean,
    val code: Int,
    val text: String,
)

/**
 * Runs one release request with OkHttp's async dispatcher so a losing source
 * in the race can be cancelled without blocking a thread.
 */
private suspend fun fetchUpdateBody(
    source: UpdateSourceConfig,
    request: Request,
): UpdateResponseBody? = suspendCancellableCoroutine<UpdateResponseBody?> { continuation ->
    val call = updateHttpClient.newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resume(null)
        }

        override fun onResponse(call: Call, response: Response) {
            val body = try {
                response.use {
                    UpdateResponseBody(
                        successful = it.isSuccessful,
                        code = it.code,
                        text = if (it.isSuccessful) it.body.string() else "",
                    )
                }
            } catch (error: Exception) {
                android.util.Log.w(
                    "UpdateCheck",
                    "${source.displayName}: cannot read release response",
                    error,
                )
                null
            }
            if (continuation.isActive) continuation.resume(body)
        }
    })
}

/** Fetches a small auxiliary file (a release-notes markdown asset). */
private suspend fun fetchText(url: String): String? =
    suspendCancellableCoroutine<String?> { continuation ->
        val call = updateHttpClient.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val text = try {
                    response.use { if (it.isSuccessful) it.body.string() else null }
                } catch (error: Exception) {
                    null
                }
                if (continuation.isActive) continuation.resume(text)
            }
        })
    }

private suspend fun parseReleaseResponse(
    source: UpdateSourceConfig,
    isCi: Boolean,
    body: String,
): LatestVersionInfo = when (source.platform) {
    UpdateSourcePlatform.CNB -> parseCnbResponse(source, isCi, body)
    UpdateSourcePlatform.GITHUB,
    UpdateSourcePlatform.GITEA_FORGEJO,
    UpdateSourcePlatform.CNB_RAW,
    -> parseJsonResponse(source, isCi, body)
}

/** GitHub and Gitea/Forgejo return a single release with an `assets` array. */
private suspend fun parseJsonResponse(
    source: UpdateSourceConfig,
    isCi: Boolean,
    body: String,
): LatestVersionInfo {
    val json = JSONObject(body)
    val assets = json.getJSONArray("assets")
    val releaseAssets = (0 until assets.length()).mapNotNull { index ->
        assets.optJSONObject(index)?.let { asset ->
            SourceReleaseAsset(
                name = asset.optString("name"),
                url = asset.optString("browser_download_url"),
            )
        }
    }
    val changelog = json.optString("body").ifBlank { fetchReleaseNotes(releaseAssets) }
    return pickReleaseAsset(source, isCi, releaseAssets, changelog)
}

/** CNB carries the whole release list inside the page's `__NEXT_DATA__` payload. */
private suspend fun parseCnbResponse(
    source: UpdateSourceConfig,
    isCi: Boolean,
    body: String,
): LatestVersionInfo {
    val releases = CnbReleaseParser.parseReleases(body, source.baseUrl)
    val release = CnbReleaseParser.pickRelease(releases, isCi)
        ?: return LatestVersionInfo()
    // CI releases may leave the release body empty and publish the notes as a
    // markdown asset; the update dialog renders it as Markdown like `body`.
    val changelog = release.changelog.ifBlank { fetchReleaseNotes(release.assets) }
    return pickReleaseAsset(source, isCi, release.assets, changelog)
}

private suspend fun fetchReleaseNotes(assets: List<SourceReleaseAsset>): String {
    val notes = (assets.firstOrNull { it.name.equals("notes.md", ignoreCase = true) }
        ?: assets.firstOrNull { it.name.endsWith(".md", ignoreCase = true) })
        ?.takeIf { it.url.startsWith("https://") || it.url.startsWith("http://") }
        ?: return ""
    return fetchText(notes.url)?.trim().orEmpty()
}

/**
 * Chooses the APK matching this device. CI builds embed the version code in
 * the asset name (the highest one wins); stable builds use the version name
 * and fall back to a universal APK only when no per-ABI build exists.
 */
private fun pickReleaseAsset(
    source: UpdateSourceConfig,
    isCi: Boolean,
    assets: List<SourceReleaseAsset>,
    changelog: String,
): LatestVersionInfo {
    val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return LatestVersionInfo()
    val downloadPrefix = source.downloadUrlPrefix()

    if (isCi) {
        // The release title has been stale in the past (assets moved to a
        // newer build before the title did), so read the version from the
        // asset names instead of trusting the title.
        val apkPattern = Regex(
            """^VeilSU_.+_(\d+)-${Regex.escape(abi)}-release\.apk$"""
        )
        val apk = assets.asSequence()
            .mapNotNull { asset ->
                val match = apkPattern.matchEntire(asset.name)
                    ?: return@mapNotNull null
                match.groupValues[1].toLongOrNull()?.let { it to asset }
            }
            .maxByOrNull { it.first }
            ?: run {
                android.util.Log.w("UpdateCheck", "${source.displayName} CI: no APK for abi=$abi")
                return LatestVersionInfo()
            }
        val downloadUrl = apk.second.url
            .takeIf { it.startsWith(downloadPrefix) }
            ?: return LatestVersionInfo()
        return LatestVersionInfo(
            versionCode = apk.first,
            downloadUrl = downloadUrl,
            changelog = changelog,
            sourceId = source.id,
            sourceName = source.displayName,
            downloadUrlPrefix = downloadPrefix,
        )
    }

    val archPattern = Regex("""^VeilSU_v(.+?)_(\d+)-${Regex.escape(abi)}-release\.apk$""")
    val universalPattern = Regex("""^VeilSU_v(.+?)_(\d+)-release\.apk$""")
    // Prefer the device-specific asset, falling back to an actual universal APK only.
    for (pattern in listOf(archPattern, universalPattern)) {
        for (asset in assets) {
            val match = pattern.matchEntire(asset.name) ?: continue
            if (!asset.url.startsWith(downloadPrefix)) continue
            return LatestVersionInfo(
                versionCode = match.groupValues[2].toLong(),
                downloadUrl = asset.url,
                changelog = changelog,
                sourceId = source.id,
                sourceName = source.displayName,
                downloadUrlPrefix = downloadPrefix,
            )
        }
    }
    return LatestVersionInfo()
}
