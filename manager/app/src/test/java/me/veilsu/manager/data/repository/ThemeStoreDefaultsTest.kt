package me.veilsu.manager.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeStoreDefaultsTest {
    @Test
    fun `empty legacy setting resolves to fixed first source`() {
        assertEquals(
            ThemeStoreDefaults.BASE_URLS.first(),
            ThemeStoreDefaults.resolveConfiguredBaseUrl(""),
        )
    }

    @Test
    fun `previous source url stays configured`() {
        val configured = "https://themes.oddnexus.top/"

        assertEquals(
            configured,
            ThemeStoreDefaults.resolveConfiguredBaseUrl(configured),
        )
    }

    @Test
    fun `every built-in source stays selectable`() {
        ThemeStoreDefaults.BASE_URLS.forEach { selected ->
            assertEquals(selected, ThemeStoreDefaults.resolveConfiguredBaseUrl(selected))
        }
    }

    @Test
    fun `custom source stays unchanged`() {
        val selected = "https://store.example.org/"

        assertEquals(selected, ThemeStoreDefaults.resolveConfiguredBaseUrl(selected))
    }
}
