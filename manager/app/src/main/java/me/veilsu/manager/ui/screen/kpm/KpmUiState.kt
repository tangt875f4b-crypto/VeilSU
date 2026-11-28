package me.veilsu.manager.ui.screen.kpm

import androidx.compose.runtime.Immutable
import me.veilsu.manager.ui.component.ConfigResult
import me.veilsu.manager.ui.viewmodel.KpmViewModel

@Immutable
data class KpmUiState(
    val operationResults: List<ConfigResult> = emptyList(),
    val isRefreshing: Boolean = false,
    val isBusy: Boolean = false,
    val hasLoaded: Boolean = false,
    val moduleList: List<KpmViewModel.ModuleInfo> = emptyList(),
    val searchQuery: String = "",
    val error: Throwable? = null,
    val showInstallModeDialog: Boolean = false,
    val tempModuleName: String? = null,
    val inputDialogState: InputDialogState = InputDialogState(),
) {
    val visibleModules: List<KpmViewModel.ModuleInfo>
        get() {
            val query = searchQuery.trim()
            return if (query.isEmpty()) moduleList else moduleList.filter { module ->
                listOf(module.id, module.name, module.description, module.author, module.version)
                    .any { it.contains(query, ignoreCase = true) }
            }
        }
}

@Immutable
data class InputDialogState(
    val visible: Boolean = false,
    val moduleId: String? = null,
    val description: String = "",
    val args: String = "",
    val commands: List<KpmCommand> = emptyList(),
    val commandsLoaded: Boolean = false,
    val isLoadingCommands: Boolean = false,
    val selectedCommand: String? = null,
    val helpText: String = "",
    val helpLoaded: Boolean = false,
    val isLoadingHelp: Boolean = false,
    val isExecuting: Boolean = false,
    val output: String = "",
    val error: String? = null,
    val exitCode: Int? = null,
    val logLines: List<String> = emptyList(),
)

@Immutable
data class KpmCommand(
    val name: String,
    val usage: String,
    val help: String,
)

@Immutable
data class KpmActions(
    val onRefresh: () -> Unit,
    val onSearchTextChange: (String) -> Unit,
    val onRequestInstall: () -> Unit,
    val onConfirmInstall: (Boolean) -> Unit,
    val onDismissInstallDialog: () -> Unit,
    val onRequestUninstall: (String) -> Unit,
    val onShowInputDialog: (String) -> Unit,
    val onHideInputDialog: () -> Unit,
    val onInputArgsChange: (String) -> Unit,
    val onSelectCommand: (String) -> Unit,
    val onLoadHelp: () -> Unit,
    val onExecuteControl: (String) -> Unit,
)
