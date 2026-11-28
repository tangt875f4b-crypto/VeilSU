package me.veilsu.manager.ui.screen.kpm

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.veilsu.manager.R
import me.veilsu.manager.ui.component.material.ExpressiveSlider
import me.veilsu.manager.ui.component.material.ExpressiveSwitch
import me.veilsu.manager.ui.component.material.TonalCard
import kotlin.math.roundToInt

/**
 * M3 Expressive control surface for a KPM module: a command picker, generated
 * argument fields (text / number / range / choice / switch), a live argument
 * preview, module help, and the execution output plus kernel log.
 */
@Composable
internal fun KpmMaterialControlDialog(
    input: InputDialogState,
    busy: Boolean,
    actions: KpmActions,
) {
    if (!input.visible) return
    val context = LocalContext.current
    var rawMode by rememberSaveable(input.moduleId) { mutableStateOf(false) }
    var rawArgs by rememberSaveable(input.moduleId) { mutableStateOf(input.args) }
    // Command whose composed arguments are currently in [rawArgs]; lets the
    // visual form survive a configuration change without mixing commands.
    var rawCommand by rememberSaveable(input.moduleId) { mutableStateOf<String?>(null) }
    val commands = input.commands
    val commandsAvailable = commands.isNotEmpty()
    val selectedCommand = commands.find { it.name == input.selectedCommand }
    val template = remember(selectedCommand) { selectedCommand?.let(::parseKpmControlTemplate) }
    val form = remember(input.moduleId, input.selectedCommand, template) {
        template?.let {
            val seed = if (rawCommand == null || rawCommand == input.selectedCommand) rawArgs else ""
            KpmControlFormState(it, seed)
        }
    }
    val showRaw = rawMode || (!commandsAvailable && input.commandsLoaded)
    val composed = if (showRaw) rawArgs else form?.compose()
    val canRun = !busy && !input.isExecuting && (showRaw || form?.compose() != null)
    // Prefer the command the module was saved with so opening the dialog does
    // not silently replace stored arguments with another command's defaults.
    val initialCommand = remember(commands, input.args) {
        val stored = kpmFirstArgumentToken(input.args)
        commands.find { it.name == stored } ?: commands.firstOrNull()
    }
    val switchMode: (Boolean) -> Unit = { toRaw ->
        if (toRaw && !rawMode) {
            rawArgs = form?.compose() ?: rawArgs
            rawMode = true
        } else if (!toRaw && rawMode) {
            form?.load(rawArgs)
            rawMode = false
            if (input.selectedCommand == null && initialCommand != null) {
                actions.onSelectCommand(initialCommand.name)
            }
        }
    }

    LaunchedEffect(commandsAvailable, rawMode, initialCommand) {
        if (!rawMode && commandsAvailable && input.selectedCommand == null) {
            initialCommand?.let { actions.onSelectCommand(it.name) }
        }
    }
    LaunchedEffect(composed, showRaw) {
        if (!showRaw && composed != null) {
            rawArgs = composed
            rawCommand = input.selectedCommand
            if (composed != input.args) actions.onInputArgsChange(composed)
        }
    }

    Dialog(
        onDismissRequest = { if (!input.isExecuting) actions.onHideInputDialog() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 20.dp)
                .fillMaxHeight(0.94f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            Icons.Rounded.Terminal,
                            null,
                            Modifier.padding(12.dp).size(26.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.kpm_control),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            input.moduleId.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = actions.onHideInputDialog,
                        enabled = !input.isExecuting,
                    ) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.cancel))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (input.description.isNotBlank()) {
                        Text(
                            input.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (commandsAvailable) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !showRaw,
                                onClick = { switchMode(false) },
                                label = { Text(stringResource(R.string.kpm_control_visual)) },
                                shape = CircleShape,
                                enabled = !input.isExecuting,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                            )
                            FilterChip(
                                selected = showRaw,
                                onClick = { switchMode(true) },
                                label = { Text(stringResource(R.string.kpm_control_raw)) },
                                shape = CircleShape,
                                enabled = !input.isExecuting,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                            )
                        }
                    }

                    if (showRaw) {
                        OutlinedTextField(
                            value = rawArgs,
                            onValueChange = {
                                rawArgs = it
                                actions.onInputArgsChange(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !input.isExecuting,
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4,
                            shape = MaterialTheme.shapes.large,
                            label = { Text(stringResource(R.string.kpm_args)) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        )
                        if (!commandsAvailable && input.commandsLoaded) {
                            Text(
                                stringResource(R.string.kpm_commands_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        if (input.isLoadingCommands) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                LinearProgressIndicator(Modifier.weight(1f))
                                Text(
                                    stringResource(R.string.kpm_commands_loading),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        } else if (commandsAvailable) {
                            Text(
                                stringResource(R.string.kpm_commands_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(commands, key = { it.name }) { command ->
                                    FilterChip(
                                        selected = command.name == input.selectedCommand,
                                        onClick = { actions.onSelectCommand(command.name) },
                                        label = { Text(command.name) },
                                        shape = CircleShape,
                                        enabled = !input.isExecuting,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        ),
                                    )
                                }
                            }
                            selectedCommand?.let { command ->
                                TonalCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            command.usage.ifBlank { command.name },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                        if (command.help.isNotBlank()) {
                                            Text(
                                                command.help,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (form == null) {
                            if (input.commandsLoaded) {
                                Text(
                                    stringResource(R.string.kpm_control_select_command),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            key(input.selectedCommand) {
                                KpmMaterialVisualArgs(form, enabled = !input.isExecuting && !busy)
                            }
                            KpmMaterialArgumentPreview(
                                args = form.compose(),
                                onCopy = { args -> copyMaterialKpmText(context, args) },
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = actions.onLoadHelp,
                            enabled = !input.isLoadingHelp && !input.isExecuting,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.kpm_show_help))
                        }
                        if (input.isLoadingHelp) {
                            LinearProgressIndicator(Modifier.weight(1f))
                        }
                    }
                    if (input.helpLoaded) {
                        KpmMaterialCodeCard(
                            text = input.helpText.ifBlank { stringResource(R.string.kpm_help_empty) },
                            context = context,
                        )
                    }

                    if (input.isExecuting) {
                        TonalCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                LoadingIndicator()
                                Text(
                                    stringResource(R.string.kpm_control_running),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    input.exitCode?.let { code ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                if (code == 0) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                                null,
                                Modifier.size(20.dp),
                                tint = if (code == 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                            )
                            Text(
                                stringResource(
                                    if (code == 0) R.string.kpm_control_success
                                    else R.string.kpm_control_failed,
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                stringResource(R.string.config_exit_code, code),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    input.error?.let { error ->
                        TonalCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                            Text(
                                error,
                                Modifier.fillMaxWidth().padding(14.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    if (input.output.isNotBlank()) {
                        Text(
                            stringResource(R.string.kpm_output),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        KpmMaterialCodeCard(input.output, context)
                    }
                    if (input.logLines.isNotEmpty()) {
                        Text(
                            stringResource(R.string.kpm_kernel_log),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        KpmMaterialCodeCard(
                            text = input.logLines.joinToString("\n"),
                            context = context,
                            copyLabel = R.string.kpm_control_copy_result,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = actions.onHideInputDialog,
                        enabled = !input.isExecuting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            val args = if (showRaw) rawArgs else form?.compose()
                            if (args != null) {
                                actions.onInputArgsChange(args)
                                actions.onExecuteControl(args)
                            }
                        },
                        enabled = canRun,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.kpm_control_run))
                    }
                }
            }
        }
    }
}

@Composable
private fun KpmMaterialVisualArgs(form: KpmControlFormState, enabled: Boolean) {
    val literals = form.template.fields
        .filter { it.kind == KpmControlKind.LITERAL }
        .map { it.literal }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (literals.isNotEmpty()) {
            Text(
                stringResource(R.string.kpm_control_fixed, literals.joinToString(" ")),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        form.template.fields.forEachIndexed { index, field ->
            when (field.kind) {
                KpmControlKind.LITERAL -> Unit
                KpmControlKind.TEXT -> KpmMaterialFieldGate(form, index, field, enabled) {
                    KpmMaterialTextField(
                        label = field.label,
                        value = form.value(index).text,
                        onValueChange = { form.updateText(index, it) },
                        enabled = enabled,
                    )
                }
                KpmControlKind.NUMBER -> KpmMaterialFieldGate(form, index, field, enabled) {
                    if (field.range != null) {
                        KpmMaterialRangeField(
                            label = field.label,
                            range = field.range,
                            value = form.value(index).text,
                            onValueChange = { form.updateText(index, it) },
                            enabled = enabled,
                        )
                    } else {
                        KpmMaterialTextField(
                            label = field.label,
                            value = form.value(index).text,
                            onValueChange = { form.updateText(index, it) },
                            enabled = enabled,
                            numeric = true,
                        )
                    }
                }
                KpmControlKind.CHOICE -> KpmMaterialFieldGate(form, index, field, enabled) {
                    KpmMaterialChoiceField(
                        label = field.label,
                        options = field.options,
                        selected = form.value(index).choice,
                        onSelected = { form.updateChoice(index, it) },
                        enabled = enabled,
                    )
                }
                KpmControlKind.TOGGLE -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            field.flag.ifBlank { field.label },
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            stringResource(R.string.kpm_control_optional),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ExpressiveSwitch(
                        checked = form.value(index).checked,
                        onCheckedChange = { form.updateChecked(index, it) },
                        enabled = enabled,
                    )
                }
                KpmControlKind.FLAG_VALUE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (field.optional) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    field.flag.ifBlank { field.label },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                )
                                ExpressiveSwitch(
                                    checked = form.value(index).checked,
                                    onCheckedChange = { form.updateChecked(index, it) },
                                    enabled = enabled,
                                )
                            }
                        } else {
                            Text(
                                field.flag.ifBlank { field.label },
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        if (!field.optional || form.value(index).checked) {
                            when {
                                field.options.isNotEmpty() -> KpmMaterialChoiceField(
                                    label = field.valueLabel,
                                    options = field.options,
                                    selected = form.value(index).choice,
                                    onSelected = { form.updateChoice(index, it) },
                                    enabled = enabled,
                                )
                                field.range != null -> KpmMaterialRangeField(
                                    label = field.valueLabel,
                                    range = field.range,
                                    value = form.value(index).text,
                                    onValueChange = { form.updateText(index, it) },
                                    enabled = enabled,
                                )
                                else -> KpmMaterialTextField(
                                    label = field.valueLabel,
                                    value = form.value(index).text,
                                    onValueChange = { form.updateText(index, it) },
                                    enabled = enabled,
                                    numeric = field.numeric,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpmMaterialFieldGate(
    form: KpmControlFormState,
    index: Int,
    field: KpmControlField,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val value = form.value(index)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (field.optional) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.kpm_control_include),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                ExpressiveSwitch(
                    checked = value.checked,
                    onCheckedChange = { form.updateChecked(index, it) },
                    enabled = enabled,
                )
            }
        }
        if (!field.optional || value.checked) content()
    }
}

@Composable
private fun KpmMaterialTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    numeric: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        label = { Text(label) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
    )
}

@Composable
private fun KpmMaterialChoiceField(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    enabled: Boolean,
) {
    val effective = selected.ifBlank { options.firstOrNull().orEmpty() }
    // Keep a raw value from loaded arguments selectable even when it is not declared.
    val visible = remember(options, effective) {
        if (effective.isNotBlank() && effective !in options) options + effective else options
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            visible.forEach { option ->
                FilterChip(
                    selected = option == effective,
                    onClick = { onSelected(option) },
                    label = { Text(option) },
                    shape = CircleShape,
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun KpmMaterialRangeField(
    label: String,
    range: IntRange,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    val start = range.first.toFloat()
    val end = range.last.toFloat()
    val state = rememberSliderState(
        value = value.toFloatOrNull()?.coerceIn(start, end) ?: start,
        trackRange = start..end,
    )
    // Keep the slider in sync when raw arguments are loaded back into the form.
    // Compare rounded values so writing the integer text back does not snap the
    // thumb while the user is still dragging.
    LaunchedEffect(value) {
        val target = value.toFloatOrNull()?.coerceIn(start, end) ?: return@LaunchedEffect
        if (target.roundToInt() != state.value.roundToInt()) state.value = target
    }
    LaunchedEffect(state.value) {
        val rounded = state.value.roundToInt().toString()
        if (rounded != value) onValueChange(rounded)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                state.value.roundToInt().toString(),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        ExpressiveSlider(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                range.first.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                range.last.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KpmMaterialArgumentPreview(
    args: String?,
    onCopy: (String) -> Unit,
) {
    TonalCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.kpm_control_preview),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (args != null) {
                    IconButton(onClick = { onCopy(args) }) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            stringResource(R.string.module_info_copy),
                            Modifier.size(18.dp),
                        )
                    }
                }
            }
            Text(
                args ?: stringResource(R.string.kpm_control_incomplete),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (args == null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun KpmMaterialCodeCard(
    text: String,
    context: Context,
    copyLabel: Int = R.string.module_info_copy,
) {
    TonalCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            IconButton(
                onClick = { copyMaterialKpmText(context, text) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    stringResource(copyLabel),
                    Modifier.size(18.dp),
                )
            }
        }
    }
}

@Suppress("UsePropertyAccessSyntax")
private fun copyMaterialKpmText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("kpm", text))
}
