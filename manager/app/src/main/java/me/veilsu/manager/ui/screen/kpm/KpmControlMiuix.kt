package me.veilsu.manager.ui.screen.kpm

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.veilsu.manager.R
import me.veilsu.manager.ui.component.miuix.AppCard
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles
import kotlin.math.roundToInt

/**
 * Miuix control surface for a KPM module. Mirrors the MD3E dialog: command
 * pills, generated argument controls, a live preview, help, and the execution
 * output plus kernel log.
 */
@Composable
internal fun KpmMiuixControlDialog(
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

    OverlayDialog(
        show = true,
        title = stringResource(R.string.kpm_control),
        summary = input.moduleId.orEmpty(),
        onDismissRequest = { if (!input.isExecuting) actions.onHideInputDialog() },
        insideMargin = DpSize(0.dp, 24.dp),
        content = {
            Column(
                modifier = Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (input.description.isNotBlank()) {
                    Text(
                        input.description,
                        style = textStyles.footnote1,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
                if (commandsAvailable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(
                            text = stringResource(R.string.kpm_control_visual),
                            onClick = { switchMode(false) },
                            enabled = !input.isExecuting,
                            modifier = Modifier.weight(1f),
                            colors = if (!showRaw) {
                                ButtonDefaults.textButtonColorsPrimary()
                            } else {
                                ButtonDefaults.textButtonColors()
                            },
                        )
                        TextButton(
                            text = stringResource(R.string.kpm_control_raw),
                            onClick = { switchMode(true) },
                            enabled = !input.isExecuting,
                            modifier = Modifier.weight(1f),
                            colors = if (showRaw) {
                                ButtonDefaults.textButtonColorsPrimary()
                            } else {
                                ButtonDefaults.textButtonColors()
                            },
                        )
                    }
                }

                if (showRaw) {
                    TextField(
                        value = rawArgs,
                        onValueChange = {
                            rawArgs = it
                            actions.onInputArgsChange(it)
                        },
                        label = stringResource(R.string.kpm_args),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !input.isExecuting,
                        useLabelAsPlaceholder = rawArgs.isEmpty(),
                        maxLines = 4,
                    )
                    if (!commandsAvailable && input.commandsLoaded) {
                        Text(
                            stringResource(R.string.kpm_commands_empty),
                            style = textStyles.footnote1,
                            color = colorScheme.onSurfaceVariantSummary,
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
                                style = textStyles.footnote1,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    } else if (commandsAvailable) {
                        Text(
                            stringResource(R.string.kpm_commands_title),
                            style = textStyles.footnote1,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            commands.forEach { command ->
                                TextButton(
                                    text = command.name,
                                    onClick = { actions.onSelectCommand(command.name) },
                                    enabled = !input.isExecuting,
                                    colors = if (command.name == input.selectedCommand) {
                                        ButtonDefaults.textButtonColorsPrimary()
                                    } else {
                                        ButtonDefaults.textButtonColors()
                                    },
                                )
                            }
                        }
                        selectedCommand?.let { command ->
                            AppCard(insideMargin = PaddingValues(14.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        command.usage.ifBlank { command.name },
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = colorScheme.onSurface,
                                    )
                                    if (command.help.isNotBlank()) {
                                        Text(
                                            command.help,
                                            style = textStyles.footnote1,
                                            color = colorScheme.onSurfaceVariantSummary,
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
                                style = textStyles.footnote1,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    } else {
                        key(input.selectedCommand) {
                            KpmMiuixVisualArgs(form, enabled = !input.isExecuting && !busy)
                        }
                        KpmMiuixArgumentPreview(
                            args = form.compose(),
                            onCopy = { args -> copyMiuixKpmText(context, args) },
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.kpm_show_help),
                        onClick = actions.onLoadHelp,
                        enabled = !input.isLoadingHelp && !input.isExecuting,
                    )
                    if (input.isLoadingHelp) {
                        LinearProgressIndicator(Modifier.weight(1f))
                    }
                }
                if (input.helpLoaded) {
                    KpmMiuixCodeCard(
                        text = input.helpText.ifBlank { stringResource(R.string.kpm_help_empty) },
                        context = context,
                    )
                }

                if (input.isExecuting) {
                    AppCard(insideMargin = PaddingValues(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Text(
                                stringResource(R.string.kpm_control_running),
                                color = colorScheme.onSurface,
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
                            imageVector = if (code == 0) Icons.Rounded.CheckCircle
                            else Icons.Rounded.Error,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (code == 0) colorScheme.primary else colorScheme.error,
                        )
                        Text(
                            text = stringResource(
                                if (code == 0) R.string.kpm_control_success
                                else R.string.kpm_control_failed,
                            ),
                            style = textStyles.body2,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.config_exit_code, code),
                            style = textStyles.footnote1,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                input.error?.let { error ->
                    AppCard(
                        insideMargin = PaddingValues(14.dp),
                    ) {
                        Text(
                            text = error,
                            style = textStyles.footnote1,
                            color = colorScheme.error,
                        )
                    }
                }
                if (input.output.isNotBlank()) {
                    Text(
                        stringResource(R.string.kpm_output),
                        style = textStyles.footnote1,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                    KpmMiuixCodeCard(input.output, context)
                }
                if (input.logLines.isNotEmpty()) {
                    Text(
                        stringResource(R.string.kpm_kernel_log),
                        style = textStyles.footnote1,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                    KpmMiuixCodeCard(
                        text = input.logLines.joinToString("\n"),
                        context = context,
                        copyLabel = R.string.kpm_control_copy_result,
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp),
                    thickness = 0.5.dp,
                    color = colorScheme.outline.copy(alpha = 0.3f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = actions.onHideInputDialog,
                        enabled = !input.isExecuting,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.kpm_control_run),
                        onClick = {
                            val args = if (showRaw) rawArgs else form?.compose()
                            if (args != null) {
                                actions.onInputArgsChange(args)
                                actions.onExecuteControl(args)
                            }
                        },
                        enabled = canRun,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        },
    )
}

@Composable
private fun KpmMiuixVisualArgs(form: KpmControlFormState, enabled: Boolean) {
    val literals = form.template.fields
        .filter { it.kind == KpmControlKind.LITERAL }
        .map { it.literal }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (literals.isNotEmpty()) {
            Text(
                stringResource(R.string.kpm_control_fixed, literals.joinToString(" ")),
                style = textStyles.footnote1,
                fontFamily = FontFamily.Monospace,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
        form.template.fields.forEachIndexed { index, field ->
            when (field.kind) {
                KpmControlKind.LITERAL -> Unit
                KpmControlKind.TEXT -> KpmMiuixFieldGate(form, index, field, enabled) {
                    KpmMiuixTextField(
                        label = field.label,
                        value = form.value(index).text,
                        onValueChange = { form.updateText(index, it) },
                        enabled = enabled,
                    )
                }
                KpmControlKind.NUMBER -> KpmMiuixFieldGate(form, index, field, enabled) {
                    if (field.range != null) {
                        KpmMiuixRangeField(
                            label = field.label,
                            range = field.range,
                            value = form.value(index).text,
                            onValueChange = { form.updateText(index, it) },
                            enabled = enabled,
                        )
                    } else {
                        KpmMiuixTextField(
                            label = field.label,
                            value = form.value(index).text,
                            onValueChange = { form.updateText(index, it) },
                            enabled = enabled,
                            numeric = true,
                        )
                    }
                }
                KpmControlKind.CHOICE -> KpmMiuixFieldGate(form, index, field, enabled) {
                    KpmMiuixChoiceField(
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
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurface,
                        )
                        Text(
                            stringResource(R.string.kpm_control_optional),
                            style = textStyles.footnote2,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Switch(
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
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = form.value(index).checked,
                                    onCheckedChange = { form.updateChecked(index, it) },
                                    enabled = enabled,
                                )
                            }
                        } else {
                            Text(
                                field.flag.ifBlank { field.label },
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurface,
                            )
                        }
                        if (!field.optional || form.value(index).checked) {
                            when {
                                field.options.isNotEmpty() -> KpmMiuixChoiceField(
                                    label = field.valueLabel,
                                    options = field.options,
                                    selected = form.value(index).choice,
                                    onSelected = { form.updateChoice(index, it) },
                                    enabled = enabled,
                                )
                                field.range != null -> KpmMiuixRangeField(
                                    label = field.valueLabel,
                                    range = field.range,
                                    value = form.value(index).text,
                                    onValueChange = { form.updateText(index, it) },
                                    enabled = enabled,
                                )
                                else -> KpmMiuixTextField(
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
private fun KpmMiuixFieldGate(
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
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
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
private fun KpmMiuixTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    numeric: Boolean = false,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = label,
        useLabelAsPlaceholder = value.isEmpty(),
        maxLines = 1,
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
    )
}

@Composable
private fun KpmMiuixChoiceField(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    enabled: Boolean,
) {
    val effective = selected.ifBlank { options.firstOrNull().orEmpty() }
    val visible = remember(options, effective) {
        if (effective.isNotBlank() && effective !in options) options + effective else options
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = textStyles.footnote1,
            color = colorScheme.onSurfaceVariantSummary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            visible.forEach { option ->
                TextButton(
                    text = option,
                    onClick = { onSelected(option) },
                    enabled = enabled,
                    colors = if (option == effective) {
                        ButtonDefaults.textButtonColorsPrimary()
                    } else {
                        ButtonDefaults.textButtonColors()
                    },
                )
            }
        }
    }
}

@Composable
private fun KpmMiuixRangeField(
    label: String,
    range: IntRange,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    val start = range.first.toFloat()
    val end = range.last.toFloat()
    val current = value.toFloatOrNull()?.coerceIn(start, end) ?: start
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = textStyles.footnote1,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f),
            )
            Text(
                current.roundToInt().toString(),
                fontFamily = FontFamily.Monospace,
                color = colorScheme.primary,
            )
        }
        Slider(
            value = current,
            onValueChange = { onValueChange(it.roundToInt().toString()) },
            valueRange = start..end,
            enabled = enabled,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                range.first.toString(),
                style = textStyles.footnote2,
                color = colorScheme.onSurfaceVariantSummary,
            )
            Text(
                range.last.toString(),
                style = textStyles.footnote2,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun KpmMiuixArgumentPreview(
    args: String?,
    onCopy: (String) -> Unit,
) {
    AppCard(insideMargin = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.kpm_control_preview),
                    style = textStyles.footnote2,
                    color = colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    args ?: stringResource(R.string.kpm_control_incomplete),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (args == null) colorScheme.error else colorScheme.onSurface,
                )
            }
            if (args != null) {
                IconButton(
                    onClick = { onCopy(args) },
                    minHeight = 36.dp,
                    minWidth = 36.dp,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Copy,
                        contentDescription = stringResource(R.string.module_info_copy),
                        modifier = Modifier.size(18.dp),
                        tint = colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun KpmMiuixCodeCard(
    text: String,
    context: Context,
    copyLabel: Int = R.string.module_info_copy,
) {
    AppCard(insideMargin = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(
                    text,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.onSurface,
                )
            }
            IconButton(
                onClick = { copyMiuixKpmText(context, text) },
                minHeight = 36.dp,
                minWidth = 36.dp,
            ) {
                Icon(
                    imageVector = MiuixIcons.Copy,
                    contentDescription = stringResource(copyLabel),
                    modifier = Modifier.size(18.dp),
                    tint = colorScheme.primary,
                )
            }
        }
    }
}

@Suppress("UsePropertyAccessSyntax")
private fun copyMiuixKpmText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("kpm", text))
}
