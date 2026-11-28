package me.veilsu.manager.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.veilsu.manager.data.repository.KpmRepository
import me.veilsu.manager.data.repository.KpmRepositoryImpl
import me.veilsu.manager.ui.component.ConfigResult
import me.veilsu.manager.ui.screen.kpm.InputDialogState
import me.veilsu.manager.ui.screen.kpm.KpmCommand
import me.veilsu.manager.ui.screen.kpm.KpmUiState
import me.veilsu.manager.ui.util.KpmRunResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class KpmViewModel(
    private val repo: KpmRepository = KpmRepositoryImpl()
) : ViewModel() {
    private val _uiState = MutableStateFlow(KpmUiState())
    val uiState: StateFlow<KpmUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null
    private var operationJob: Job? = null
    private var controlJob: Job? = null
    private var tempFile: File? = null

    // Reserve before launching so rapid clicks cannot start concurrent root operations.
    fun runOperation(block: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        operationJob = viewModelScope.launch {
            try {
                refreshJob?.join()
                block()
            } finally {
                _uiState.update { it.copy(isBusy = false) }
                fetchModuleList()
            }
        }
    }

    fun prepareInstall(file: File, moduleName: String) {
        clearTempFile()
        tempFile = file
        _uiState.update { it.copy(showInstallModeDialog = true, tempModuleName = moduleName) }
    }

    fun clearTempFile() {
        tempFile?.delete()
        tempFile = null
    }

    fun getTempFile(): File? = tempFile

    fun setOperationResult(result: ConfigResult) {
        _uiState.update { it.copy(operationResults = listOf(result)) }
    }

    fun fetchModuleList(silent: Boolean = false) {
        if (refreshJob?.isActive == true || _uiState.value.isBusy) return
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = !silent || !it.hasLoaded) }
            try {
                repo.getModuleList()
                    .onSuccess { modules ->
                        _uiState.update {
                            it.copy(moduleList = modules, hasLoaded = true, error = null)
                        }
                    }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        Log.e("KpmViewModel", "Failed to refresh modules", e)
                        _uiState.update { it.copy(error = e) }
                    }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun dismissInstallDialog() {
        _uiState.update { it.copy(showInstallModeDialog = false, tempModuleName = null) }
    }

    fun showInputDialog(moduleId: String) {
        if (_uiState.value.isBusy) return
        val module = _uiState.value.moduleList.find { it.id == moduleId }
        _uiState.update {
            it.copy(inputDialogState = InputDialogState(
                visible = true,
                moduleId = moduleId,
                description = module?.description.orEmpty(),
                args = module?.args.orEmpty(),
                isLoadingCommands = true,
            ))
        }
        loadCommands(moduleId)
    }

    fun hideInputDialog() {
        if (_uiState.value.isBusy || _uiState.value.inputDialogState.isExecuting) return
        controlJob?.cancel()
        _uiState.update { it.copy(inputDialogState = InputDialogState()) }
    }

    fun updateInputArgs(args: String) {
        _uiState.update { it.copy(inputDialogState = it.inputDialogState.copy(args = args)) }
    }

    fun selectCommand(name: String) {
        val input = _uiState.value.inputDialogState
        val command = input.commands.find { it.name == name } ?: return
        // The dialog owns argument composition; switching commands must not
        // overwrite the raw arguments the user already entered.
        _uiState.update {
            it.copy(inputDialogState = it.inputDialogState.copy(selectedCommand = command.name))
        }
    }

    private fun loadCommands(moduleId: String) {
        viewModelScope.launch {
            val result = repo.getModuleCommands(moduleId)
            val commands = result.getOrNull()?.let(::parseCommandList).orEmpty()
            _uiState.update { state ->
                val input = state.inputDialogState
                if (!input.visible || input.moduleId != moduleId) state
                else state.copy(inputDialogState = input.copy(
                    commands = commands,
                    commandsLoaded = true,
                    isLoadingCommands = false,
                ))
            }
        }
    }

    fun loadHelp() {
        val input = _uiState.value.inputDialogState
        val moduleId = input.moduleId ?: return
        if (input.isLoadingHelp) return
        _uiState.update {
            it.copy(inputDialogState = it.inputDialogState.copy(isLoadingHelp = true))
        }
        viewModelScope.launch {
            val help = repo.getModuleHelp(moduleId).getOrElse { e ->
                if (e is CancellationException) throw e
                Log.e("KpmViewModel", "Failed to load KPM help", e)
                ""
            }
            _uiState.update { state ->
                val current = state.inputDialogState
                if (!current.visible || current.moduleId != moduleId) state
                else state.copy(inputDialogState = current.copy(
                    helpText = help,
                    helpLoaded = true,
                    isLoadingHelp = false,
                ))
            }
        }
    }

    fun executeControl(args: String? = null) {
        val input = _uiState.value.inputDialogState
        val moduleId = input.moduleId ?: return
        if (input.isExecuting) return
        val commandArgs = args ?: input.args
        _uiState.update {
            it.copy(inputDialogState = it.inputDialogState.copy(
                args = commandArgs,
                isExecuting = true,
                output = "",
                error = null,
                exitCode = null,
                logLines = emptyList(),
            ))
        }

        controlJob = viewModelScope.launch {
            val logJob = launch(Dispatchers.IO) {
                while (isActive) {
                    val lines = repo.readKernelLog(moduleId)
                    _uiState.update { state ->
                        val current = state.inputDialogState
                        if (!current.visible || current.moduleId != moduleId) state
                        else state.copy(inputDialogState = current.copy(logLines = lines))
                    }
                    delay(800)
                }
            }
            val result: KpmRunResult = repo.runModule(moduleId, commandArgs).getOrElse { e ->
                if (e is CancellationException) throw e
                Log.e("KpmViewModel", "Failed to run KPM command", e)
                KpmRunResult(-1, "", e.localizedMessage.orEmpty())
            }
            logJob.cancel()
            val finalLog = repo.readKernelLog(moduleId)
            _uiState.update { state ->
                val current = state.inputDialogState
                if (!current.visible || current.moduleId != moduleId) state
                else state.copy(inputDialogState = current.copy(
                    isExecuting = false,
                    output = result.output,
                    error = result.error.takeIf { it.isNotBlank() },
                    exitCode = result.code,
                    logLines = finalLog,
                ))
            }
        }
    }

    private fun parseCommandList(raw: String): List<KpmCommand> {
        val first = raw.lineSequence().firstOrNull() ?: return emptyList()
        if (!first.startsWith("KPM-COMMANDS")) return emptyList()
        return raw.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val fields = line.split('\t', limit = 3)
                if (fields.size < 3 || fields[0].isBlank()) null
                else KpmCommand(fields[0].trim(), fields[1].trim(), fields[2].trim())
            }
            .take(16)
            .toList()
    }

    fun updateSearchText(text: String) {
        _uiState.update { it.copy(searchQuery = text) }
    }

    override fun onCleared() {
        controlJob?.cancel()
        // Shell execution can still be reading the staged file after cancellation.
        val operation = operationJob
        if (operation == null || operation.isCompleted) clearTempFile()
        else operation.invokeOnCompletion { clearTempFile() }
    }

    data class ModuleInfo(
        val id: String,
        val name: String,
        val version: String,
        val author: String,
        val description: String,
        val args: String,
        val enabled: Boolean,
        val hasAction: Boolean
    )
}
