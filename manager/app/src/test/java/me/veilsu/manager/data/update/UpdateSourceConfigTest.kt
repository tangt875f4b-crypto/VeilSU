package me.veilsu.manager.data.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateSourceConfigTest {

    @Test
    fun defaultSourceIsVeilSUGitHubReleases() {
        val source = UpdateSourceDefaults.DEFAULT

        assertEquals(UpdateSourcePlatform.GITHUB, source.platform)
        assertEquals(
            "https://api.github.com/repos/tangt875f4b-crypto/VeilSU/releases/latest",
            source.releasesApiUrl(UpdateSourceDefaults.STABLE_ENDPOINT),
        )
        assertEquals(
            "https://api.github.com/repos/tangt875f4b-crypto/VeilSU/releases/tags/manager-ci",
            source.releasesApiUrl("tags/${UpdateSourceDefaults.CI_TAG}"),
        )
        assertEquals(
            "https://github.com/tangt875f4b-crypto/VeilSU/releases/download/",
            source.downloadUrlPrefix(),
        )
    }

    @Test
    fun devTagMapsToItsOwnReleaseTag() {
        assertEquals(
            "https://api.github.com/repos/tangt875f4b-crypto/VeilSU/releases/tags/manager-dev",
            UpdateSourceDefaults.DEFAULT.releasesApiUrl("tags/manager-dev"),
        )
    }
}
