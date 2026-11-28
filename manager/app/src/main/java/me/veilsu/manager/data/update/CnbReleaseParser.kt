package me.veilsu.manager.data.update

import org.json.JSONObject

/** A release attachment with an absolute download URL. */
data class SourceReleaseAsset(
    val name: String,
    val url: String,
)

/** The subset of a release the update checker and the pinger care about. */
data class SourceRelease(
    val tag: String,
    val changelog: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val latest: Boolean,
    val publishedAt: String,
    val assets: List<SourceReleaseAsset>,
)

/**
 * CNB (cnb.cool) does not expose an anonymous release API: `api.cnb.cool`
 * requires an account token even for public repositories. The web front end
 * is public, though, and server-renders release data into the Next.js
 * `__NEXT_DATA__` payload, so the manager reads that payload instead.
 *
 * The list page embeds at most ten releases, while the `/latest` and
 * `/tag/<tag>` pages embed exactly the requested release. Both shapes are
 * parsed here: the list keys are snake_case, the single-release keys camelCase.
 *
 * Update checks therefore depend on the page's data shape. Everything is
 * best-effort: when the payload cannot be found the source simply returns no
 * release and the other sources in the race keep working.
 */
object CnbReleaseParser {
    private val nextDataPattern = Regex(
        """<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    fun parseReleases(html: String, baseUrl: String): List<SourceRelease> {
        val payload = nextDataPattern.find(html)?.groupValues?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return emptyList()
        val props = root.optJSONObject("props")
        val state = props?.optJSONObject("pageProps")?.optJSONObject("initialState")
            ?: props?.optJSONObject("initialState")
            ?: return emptyList()

        val origin = baseUrl.trimEnd('/')
        val releases = state.optJSONObject("slug")
            ?.optJSONObject("repo")
            ?.optJSONObject("releases")
            ?: return emptyList()

        val list = releases.optJSONObject("list")
            ?.optJSONObject("data")
            ?.optJSONArray("releases")
        if (list != null && list.length() > 0) {
            return (0 until list.length()).mapNotNull { index ->
                list.optJSONObject(index)?.toSourceRelease(origin)
            }
        }

        // A `/latest` or `/tag/<tag>` page renders one release under `detail`
        // instead of the paged list.
        val detail = releases.optJSONObject("detail")
            ?.optJSONObject("data")
            ?.optJSONObject("release")
        return listOfNotNull(detail?.toSourceRelease(origin, camelCase = true))
    }

    /**
     * The `manager-ci` tagged release for CI checks, otherwise the newest
     * published, non-draft, non-prerelease release (preferring the one CNB
     * flags as latest).
     */
    fun pickRelease(releases: List<SourceRelease>, isCi: Boolean): SourceRelease? =
        if (isCi) {
            releases.firstOrNull { it.tag == UpdateSourceDefaults.CI_TAG }
        } else {
            releases
                .filter { !it.draft && !it.prerelease }
                .let { stable ->
                    stable.firstOrNull { it.latest }
                        ?: stable.maxByOrNull { it.publishedAt }
                }
        }

    /** Latest CI release even when it is flagged as a prerelease, used by ping. */
    fun pickPingRelease(releases: List<SourceRelease>): SourceRelease? =
        pickRelease(releases, isCi = false)
            ?: releases.firstOrNull { release ->
                release.assets.any { it.name.endsWith(".apk") }
            }
            ?: releases.firstOrNull()

    /**
     * The list page uses snake_case keys; a single-release page (`/latest`,
     * `/tag/<tag>`) uses camelCase ones. Assets share the same keys in both.
     */
    private fun JSONObject.toSourceRelease(origin: String, camelCase: Boolean = false): SourceRelease? {
        val tag = (if (camelCase) optString("tagRef") else optString("tag_ref"))
            .removePrefix("refs/tags/")
        if (tag.isBlank()) return null

        val assetArray = optJSONArray("assets")
        val assets = if (assetArray == null) {
            emptyList()
        } else {
            (0 until assetArray.length()).mapNotNull { index ->
                val asset = assetArray.optJSONObject(index) ?: return@mapNotNull null
                val name = asset.optString("name")
                val path = asset.optString("path")
                if (name.isBlank() || path.isBlank()) return@mapNotNull null
                SourceReleaseAsset(name = name, url = origin + path)
            }
        }

        return SourceRelease(
            tag = tag,
            changelog = optString("body"),
            draft = optBoolean(if (camelCase) "isDraft" else "is_draft"),
            prerelease = optBoolean(if (camelCase) "isPrerelease" else "is_prerelease"),
            latest = optBoolean(if (camelCase) "isLatest" else "is_latest"),
            publishedAt = optString(if (camelCase) "publishedAt" else "published_at"),
            assets = assets,
        )
    }
}
