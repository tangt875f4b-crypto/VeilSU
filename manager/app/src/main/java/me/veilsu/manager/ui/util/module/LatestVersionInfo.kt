package me.veilsu.manager.ui.util.module

data class LatestVersionInfo(
    val versionCode: Long = 0L,
    val downloadUrl: String = "",
    val changelog: String = "",
    /** Id of the update source that produced this result; blank for the legacy default. */
    val sourceId: String = "",
    val sourceName: String = "",
    /** Download URL prefix accepted for this result. Blank falls back to the built-in source. */
    val downloadUrlPrefix: String = "",
)
