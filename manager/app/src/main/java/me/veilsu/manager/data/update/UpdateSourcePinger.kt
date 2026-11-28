package me.veilsu.manager.data.update

import android.os.Build
import me.veilsu.manager.ksuApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Result of probing one update source: release API latency and a transfer speed sample. */
data class UpdateSourcePingResult(
    val apiLatencyMs: Long? = null,
    val downloadKbps: Long? = null,
    val error: String? = null,
)

private const val SPEED_TEST_BYTES = 512 * 1024
private const val SPEED_TEST_MIN_BYTES = 64 * 1024

/**
 * Pings a source by requesting its stable release metadata (the same endpoint
 * the update checker uses), then downloads a small ranged slice of an APK
 * asset to estimate download throughput. Both steps are best-effort: a source
 * without assets still reports API latency.
 */
suspend fun pingUpdateSource(source: UpdateSourceConfig): UpdateSourcePingResult =
    withContext(Dispatchers.IO) {
        val client = ksuApp.okhttpClient.newBuilder()
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
        try {
            val request = Request.Builder()
                .url(source.releasesApiUrl(UpdateSourceDefaults.STABLE_ENDPOINT))
                .header("Accept", source.acceptHeader)
                .build()

            val started = System.nanoTime()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateSourcePingResult(error = "HTTP ${response.code}")
                }
                val body = response.body.string()
                val apiLatencyMs = (System.nanoTime() - started) / 1_000_000
                val assetUrl = when (source.platform) {
                    UpdateSourcePlatform.CNB ->
                        CnbReleaseParser.pickPingRelease(
                            CnbReleaseParser.parseReleases(body, source.baseUrl),
                        )?.let { pickPingAsset(it.assets) }

                    UpdateSourcePlatform.GITHUB,
                    UpdateSourcePlatform.GITEA_FORGEJO,
                    UpdateSourcePlatform.CNB_RAW,
                    -> pickPingAsset(parseJsonAssets(body))
                }
                val downloadKbps = assetUrl?.let { measureDownloadKbps(client, it) }
                UpdateSourcePingResult(
                    apiLatencyMs = apiLatencyMs,
                    downloadKbps = downloadKbps,
                )
            }
        } catch (e: Exception) {
            UpdateSourcePingResult(
                error = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName,
            )
        }
    }

private fun parseJsonAssets(body: String): List<SourceReleaseAsset> {
    val assets = JSONObject(body).optJSONArray("assets") ?: return emptyList()
    return (0 until assets.length()).mapNotNull { index ->
        assets.optJSONObject(index)?.let { asset ->
            SourceReleaseAsset(
                name = asset.optString("name"),
                url = asset.optString("browser_download_url"),
            )
        }
    }
}

private fun pickPingAsset(assets: List<SourceReleaseAsset>): String? {
    val abi = Build.SUPPORTED_ABIS.firstOrNull()
    val preferred = assets.firstOrNull { asset ->
        abi != null && asset.name.endsWith("-$abi-release.apk")
    } ?: assets.firstOrNull { it.name.endsWith(".apk") }
    return preferred?.url?.takeIf { it.isNotBlank() }
}

private fun measureDownloadKbps(client: OkHttpClient, url: String): Long? = runCatching {
    val request = Request.Builder()
        .url(url)
        .header("Range", "bytes=0-${SPEED_TEST_BYTES - 1}")
        .header("Accept-Encoding", "identity")
        .build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return@use null
        val input = response.body.byteStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        var started = 0L
        while (total < SPEED_TEST_BYTES) {
            val count = input.read(buffer)
            if (count < 0) break
            if (total == 0L) started = System.nanoTime()
            total += count
        }
        if (started == 0L || total < SPEED_TEST_MIN_BYTES) {
            null
        } else {
            // Measure transfer only, excluding connection establishment.
            val elapsedMs = ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(1)
            total * 1000 / 1024 / elapsedMs
        }
    }
}.getOrNull()
