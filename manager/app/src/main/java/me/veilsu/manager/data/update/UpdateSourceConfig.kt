package me.veilsu.manager.data.update

import android.net.Uri
import kotlinx.serialization.Serializable

/**
 * Hosting platform of an update source. GitHub and Gitea/Forgejo expose the
 * same release JSON shape (`assets[].name`/`browser_download_url`/`body`), but
 * their API roots and download hosts differ. CNB has no anonymous release
 * API, so its legacy release pages are parsed by [CnbReleaseParser]. New CNB
 * sources use [CNB_RAW]: a `manifest.json` committed to a branch, with assets
 * served from `/-/git/raw/`.
 */
@Serializable
enum class UpdateSourcePlatform {
    GITHUB,

    /** Gitea and Forgejo share one REST API; the label covers both. */
    GITEA_FORGEJO,

    /** cnb.cool; releases are parsed from the public release page. */
    CNB,

    /**
     * A CNB repository that publishes ordinary files instead of releases.
     * Each channel keeps a `manifest.json` on its own branch, and the APK
     * links are raw file URLs. This is the default source now that CNB
     * releases are no longer used for builds.
     */
    CNB_RAW,
}

/**
 * A release repository the manager can pull stable and CI updates from.
 * [baseUrl] is the site origin without a trailing slash; [owner]/[repo] point
 * at the repository on that site.
 */
@Serializable
data class UpdateSourceConfig(
    val id: String,
    val name: String = "",
    val platform: UpdateSourcePlatform = UpdateSourcePlatform.GITEA_FORGEJO,
    val baseUrl: String = "",
    val owner: String = "",
    val repo: String = "",
    val enabled: Boolean = true,
) {
    val displayName: String
        get() = name.ifBlank { "$owner/$repo" }

    val isGitHub: Boolean
        get() = platform == UpdateSourcePlatform.GITHUB

    /** `Accept` header the release request should carry. */
    val acceptHeader: String
        get() = when (platform) {
            UpdateSourcePlatform.GITHUB -> "application/vnd.github+json"
            UpdateSourcePlatform.GITEA_FORGEJO,
            UpdateSourcePlatform.CNB_RAW,
            -> "application/json"

            UpdateSourcePlatform.CNB -> "text/html"
        }

    /** `latest` or `tags/<tag>` below the releases collection. */
    fun releasesApiUrl(endpoint: String): String = when (platform) {
        UpdateSourcePlatform.GITHUB ->
            "https://api.github.com/repos/$owner/$repo/releases/$endpoint"

        UpdateSourcePlatform.GITEA_FORGEJO ->
            "$baseUrl/api/v1/repos/$owner/$repo/releases/$endpoint"

        // CNB offers no anonymous JSON API, so its web pages are read. The
        // plain list only server-renders the first page (10 releases), which a
        // busy artifact repository pushes the rolling tag and the newest stable
        // release off; the `/latest` and `/tag/<tag>` pages embed the single
        // release the caller asked for, so they are always used instead.
        UpdateSourcePlatform.CNB -> when {
            endpoint == UpdateSourceDefaults.STABLE_ENDPOINT ->
                "$baseUrl/$owner/$repo/-/releases/latest"

            endpoint.startsWith("tags/") ->
                "$baseUrl/$owner/$repo/-/releases/tag/${endpoint.removePrefix("tags/")}"

            else -> "$baseUrl/$owner/$repo/-/releases"
        }

        // The raw channel stores one manifest per branch. A CI tag maps to the
        // branch of the same name (`manager-ci`, `manager-dev`); the stable
        // channel reads the `stable` branch written by the release workflow.
        UpdateSourcePlatform.CNB_RAW -> {
            val branch = when {
                endpoint == UpdateSourceDefaults.STABLE_ENDPOINT ->
                    UpdateSourceDefaults.RAW_STABLE_BRANCH

                endpoint.startsWith("tags/") -> endpoint.removePrefix("tags/")

                else -> UpdateSourceDefaults.RAW_CI_BRANCH
            }
            "$baseUrl/$owner/$repo/-/git/raw/$branch/${UpdateSourceDefaults.RAW_MANIFEST}"
        }
    }

    /** Repository metadata endpoint, used to probe a source without fetching releases. */
    fun repoApiUrl(): String = when (platform) {
        UpdateSourcePlatform.GITHUB -> "https://api.github.com/repos/$owner/$repo"
        UpdateSourcePlatform.GITEA_FORGEJO -> "$baseUrl/api/v1/repos/$owner/$repo"
        UpdateSourcePlatform.CNB,
        UpdateSourcePlatform.CNB_RAW,
        -> "$baseUrl/$owner/$repo"
    }

    /** Prefix every release asset URL of this source must start with. */
    fun downloadUrlPrefix(): String = when (platform) {
        UpdateSourcePlatform.GITHUB ->
            "https://github.com/$owner/$repo/releases/download/"

        UpdateSourcePlatform.GITEA_FORGEJO ->
            "$baseUrl/$owner/$repo/releases/download/"

        UpdateSourcePlatform.CNB ->
            "$baseUrl/$owner/$repo/-/releases/download/"

        UpdateSourcePlatform.CNB_RAW ->
            "$baseUrl/$owner/$repo/-/git/raw/"
    }

    fun matches(parsed: ParsedUpdateSource): Boolean =
        baseUrl.equals(parsed.baseUrl, ignoreCase = true) &&
            owner.equals(parsed.owner, ignoreCase = true) &&
            repo.equals(parsed.repo, ignoreCase = true)
}

/** Result of parsing a pasted repository URL. */
data class ParsedUpdateSource(
    val platform: UpdateSourcePlatform,
    val baseUrl: String,
    val owner: String,
    val repo: String,
)

object UpdateSourceDefaults {
    /** Default source id. VeilSU never shipped the CNB default, so this is `github`. */
    const val DEFAULT_ID = "github"
    const val DEFAULT_BASE_URL = "https://github.com"
    const val DEFAULT_OWNER = "tangt875f4b-crypto"
    const val DEFAULT_REPO = "VeilSU"

    const val CODEBERG_ID = "codeberg"
    const val CODEBERG_BASE_URL = "https://codeberg.org"
    const val CODEBERG_OWNER = "ShirkNeko"
    const val CODEBERG_REPO = "VeilSU"

    const val CI_TAG = "manager-ci"
    const val STABLE_ENDPOINT = "latest"

    /** Every raw channel writes this file at the root of its branch. */
    const val RAW_MANIFEST = "manifest.json"
    const val RAW_CI_BRANCH = "manager-ci"
    const val RAW_STABLE_BRANCH = "stable"

    /** Upper bound for custom sources; built-in sources are always present. */
    const val MAX_CUSTOM_SOURCES = 10

    /** Default source: GitHub releases where VeilSU CI publishes manager builds. */
    val DEFAULT = UpdateSourceConfig(
        id = DEFAULT_ID,
        name = "",
        platform = UpdateSourcePlatform.GITHUB,
        baseUrl = DEFAULT_BASE_URL,
        owner = DEFAULT_OWNER,
        repo = DEFAULT_REPO,
        enabled = true,
    )

    /** Codeberg mirror (Forgejo API), always checked; cannot be removed. */
    val CODEBERG = UpdateSourceConfig(
        id = CODEBERG_ID,
        name = "",
        platform = UpdateSourcePlatform.GITEA_FORGEJO,
        baseUrl = CODEBERG_BASE_URL,
        owner = CODEBERG_OWNER,
        repo = CODEBERG_REPO,
        enabled = true,
    )

    /** All built-in sources; the default CNB source is listed first. */
    val BUILTIN_SOURCES: List<UpdateSourceConfig> = listOf(DEFAULT, CODEBERG)

    fun isBuiltinId(id: String): Boolean = BUILTIN_SOURCES.any { it.id == id }
}

/**
 * Parses pasted repository links such as
 * `https://github.com/owner/repo`, `https://git.example.com/owner/repo.git`,
 * `git.example.com/owner/repo` or `git@github.com:owner/repo.git`.
 *
 * GitHub and CNB hosts are detected automatically; every other host is
 * treated as Gitea/Forgejo because both offer the same API. Extra path
 * segments after `owner/repo` (a release page, issues, …) are ignored.
 */
fun parseUpdateSourceUrl(raw: String): ParsedUpdateSource? {
    var text = raw.trim()
    if (text.isEmpty()) return null

    // Accept scp-like SSH remote addresses too.
    if (text.startsWith("git@") && text.contains(':')) {
        val hostAndPath = text.removePrefix("git@")
        text = "https://" + hostAndPath.replaceFirst(":", "/")
    }
    if (!text.contains("://")) text = "https://$text"

    val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "https" && scheme != "http") return null
    val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return null

    val segments = uri.pathSegments
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()
    if (segments.size < 2) return null

    val owner = segments[0]
    val repo = segments[1].removeSuffix(".git")
    if (owner.isBlank() || repo.isBlank()) return null

    val port = if (uri.port != -1) ":${uri.port}" else ""
    val baseUrl = "$scheme://$host$port"
    val platform = when {
        host == "github.com" || host.endsWith(".github.com") -> UpdateSourcePlatform.GITHUB
        // New CNB sources use the raw manifest. The legacy CNB release parser
        // stays in the enum for source entries saved by older builds.
        host == "cnb.cool" || host.endsWith(".cnb.cool") -> UpdateSourcePlatform.CNB_RAW
        else -> UpdateSourcePlatform.GITEA_FORGEJO
    }
    return ParsedUpdateSource(
        platform = platform,
        baseUrl = baseUrl,
        owner = owner,
        repo = repo,
    )
}
