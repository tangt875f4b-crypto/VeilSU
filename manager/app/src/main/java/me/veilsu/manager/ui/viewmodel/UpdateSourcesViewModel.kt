package me.veilsu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.veilsu.manager.data.repository.SettingsRepository
import me.veilsu.manager.data.repository.SettingsRepositoryImpl
import me.veilsu.manager.data.update.UpdateSourceConfig
import me.veilsu.manager.data.update.UpdateSourceDefaults
import me.veilsu.manager.data.update.parseUpdateSourceUrl
import me.veilsu.manager.data.update.pingUpdateSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class AddUpdateSourceResult {
    ADDED,
    INVALID_URL,
    DUPLICATE,
    LIMIT_REACHED,
}

/**
 * Backs the update source manager. Configuration is stored immediately, so the
 * About page toggle and the next home refresh see the change without a save
 * button. Ping results are session-only.
 */
class UpdateSourcesViewModel(
    private val settings: SettingsRepository = SettingsRepositoryImpl(),
) : ViewModel() {

    data class PingState(
        val running: Boolean = false,
        val apiLatencyMs: Long? = null,
        val downloadKbps: Long? = null,
        val error: String? = null,
    )

    data class UiState(
        val builtinSources: List<UpdateSourceConfig> = UpdateSourceDefaults.BUILTIN_SOURCES,
        val customSources: List<UpdateSourceConfig> = emptyList(),
        val pinnedId: String = "",
        val pings: Map<String, PingState> = emptyMap(),
    ) {
        val allSources: List<UpdateSourceConfig>
            get() = builtinSources + customSources
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    private fun reload() {
        val customSources = settings.updateSources
        val storedPin = settings.pinnedUpdateSourceId
        // A pin can outlive its source (removed built-in or deleted custom
        // source). Show the default preference the checker will actually use.
        val resolvedPin = if (
            storedPin.isBlank() ||
            UpdateSourceDefaults.isBuiltinId(storedPin) ||
            customSources.any { it.id == storedPin }
        ) {
            storedPin
        } else {
            UpdateSourceDefaults.DEFAULT_ID
        }
        _uiState.update {
            it.copy(
                customSources = customSources,
                pinnedId = resolvedPin,
            )
        }
    }

    fun addSource(url: String, name: String): AddUpdateSourceResult {
        val parsed = parseUpdateSourceUrl(url) ?: return AddUpdateSourceResult.INVALID_URL
        val existing = settings.updateSources
        if (existing.size >= UpdateSourceDefaults.MAX_CUSTOM_SOURCES) {
            return AddUpdateSourceResult.LIMIT_REACHED
        }
        if (UpdateSourceDefaults.BUILTIN_SOURCES.any { it.matches(parsed) } ||
            existing.any { it.matches(parsed) }
        ) {
            return AddUpdateSourceResult.DUPLICATE
        }

        val source = UpdateSourceConfig(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            platform = parsed.platform,
            baseUrl = parsed.baseUrl,
            owner = parsed.owner,
            repo = parsed.repo,
        )
        settings.updateSources = existing + source
        reload()
        return AddUpdateSourceResult.ADDED
    }

    fun removeSource(id: String) {
        settings.updateSources = settings.updateSources.filterNot { it.id == id }
        if (settings.pinnedUpdateSourceId == id) settings.pinnedUpdateSourceId = ""
        _uiState.update { it.copy(pings = it.pings - id) }
        reload()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        settings.updateSources = settings.updateSources.map { source ->
            if (source.id == id) source.copy(enabled = enabled) else source
        }
        if (!enabled && settings.pinnedUpdateSourceId == id) {
            settings.pinnedUpdateSourceId = ""
        }
        reload()
    }

    fun setPinned(id: String) {
        // Compare against the resolved pin shown in the UI, not the raw
        // preference, so a stale pin still toggles off on the first tap.
        if (_uiState.value.pinnedId == id) {
            settings.pinnedUpdateSourceId = ""
        } else {
            // Pinning a disabled source would make the pin unusable.
            settings.updateSources = settings.updateSources.map { source ->
                if (source.id == id) source.copy(enabled = true) else source
            }
            settings.pinnedUpdateSourceId = id
        }
        reload()
    }

    fun ping(sourceId: String) {
        val source = _uiState.value.allSources.firstOrNull { it.id == sourceId } ?: return
        if (_uiState.value.pings[sourceId]?.running == true) return
        _uiState.update { it.copy(pings = it.pings + (sourceId to PingState(running = true))) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { pingUpdateSource(source) }
            _uiState.update { state ->
                state.copy(
                    pings = state.pings + (
                        sourceId to PingState(
                            running = false,
                            apiLatencyMs = result.apiLatencyMs,
                            downloadKbps = result.downloadKbps,
                            error = result.error,
                        )
                    )
                )
            }
        }
    }

    fun pingAll() {
        _uiState.value.allSources.forEach { ping(it.id) }
    }
}
