package me.veilsu.manager.ui.screen.updatesources

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import me.veilsu.manager.ui.component.material.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.veilsu.manager.R
import me.veilsu.manager.data.update.UpdateSourceConfig
import me.veilsu.manager.data.update.UpdateSourceDefaults
import me.veilsu.manager.data.update.UpdateSourcePlatform
import me.veilsu.manager.data.update.parseUpdateSourceUrl
import me.veilsu.manager.ui.component.material.ExpressiveScaffold
import me.veilsu.manager.ui.component.material.TopBarBackButton
import me.veilsu.manager.ui.navigation3.LocalNavigator
import me.veilsu.manager.ui.viewmodel.AddUpdateSourceResult
import me.veilsu.manager.ui.viewmodel.UpdateSourcesViewModel
import java.util.Locale

/**
 * Managed from the About page's top bar. The built-in sources are always shown
 * and cannot be removed; custom GitHub/Gitea/Forgejo/CNB repositories are optional.
 */
@Composable
fun UpdateSourcesScreen(viewModel: UpdateSourcesViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<UpdateSourceConfig?>(null) }

    ExpressiveScaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                title = {
                    Text(
                        text = stringResource(R.string.update_sources_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    TextButton(onClick = viewModel::pingAll) {
                        Text(stringResource(R.string.update_sources_ping_all))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") {
                SummaryCard(state)
            }
            item(key = "default-title") {
                SectionTitle(R.string.update_sources_default_source)
            }
            items(state.builtinSources, key = { "builtin-${it.id}" }) { source ->
                UpdateSourceCard(
                    source = source,
                    title = sourceDisplayTitle(source),
                    isDefault = true,
                    isPinned = state.pinnedId == source.id,
                    ping = state.pings[source.id],
                    onPing = { viewModel.ping(source.id) },
                    onPin = { viewModel.setPinned(source.id) },
                    onEnabledChange = {},
                    onDelete = null,
                )
            }
            item(key = "custom-title") {
                SectionTitle(R.string.update_sources_custom_sources)
            }
            if (state.customSources.isEmpty()) {
                item(key = "custom-empty") {
                    Text(
                        text = stringResource(R.string.update_sources_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            } else {
                items(state.customSources, key = { it.id }) { source ->
                    UpdateSourceCard(
                        source = source,
                        title = source.displayName,
                        isDefault = false,
                        isPinned = state.pinnedId == source.id,
                        ping = state.pings[source.id],
                        onPing = { viewModel.ping(source.id) },
                        onPin = { viewModel.setPinned(source.id) },
                        onEnabledChange = { viewModel.setEnabled(source.id, it) },
                        onDelete = { pendingDelete = source },
                    )
                }
            }
            item(key = "add") {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.update_sources_add))
                }
            }
        }
    }

    if (showAddDialog) {
        AddUpdateSourceDialog(
            onDismiss = { showAddDialog = false },
            onAdd = viewModel::addSource,
        )
    }

    pendingDelete?.let { source ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.update_sources_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.update_sources_delete_message,
                        source.displayName,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeSource(source.id)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.update_sources_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.update_sources_cancel))
                }
            },
        )
    }
}

@Composable
private fun SummaryCard(state: UpdateSourcesViewModel.UiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.update_sources_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val pinnedName = state.allSources
                .firstOrNull { it.id == state.pinnedId }
                ?.let { sourceDisplayTitle(it) }
            Text(
                text = if (pinnedName == null) {
                    stringResource(R.string.update_sources_auto_hint)
                } else {
                    stringResource(R.string.update_sources_pinned_hint, pinnedName)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.update_sources_ping_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

@Composable
private fun sourceDisplayTitle(source: UpdateSourceConfig): String = when (source.id) {
    UpdateSourceDefaults.CODEBERG_ID -> stringResource(R.string.update_sources_codeberg_name)
    UpdateSourceDefaults.DEFAULT_ID -> stringResource(R.string.update_sources_cnb_name)
    else -> source.displayName
}

@Composable
private fun UpdateSourceCard(
    source: UpdateSourceConfig,
    title: String,
    isDefault: Boolean,
    isPinned: Boolean,
    ping: UpdateSourcesViewModel.PingState?,
    onPing: () -> Unit,
    onPin: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isDefault) {
                            SourceBadge(stringResource(R.string.update_sources_default_badge))
                        }
                        if (isPinned) {
                            SourceBadge(stringResource(R.string.update_sources_pinned_badge))
                        }
                    }
                    Text(
                        text = stringResource(
                            when (source.platform) {
                                UpdateSourcePlatform.GITHUB -> R.string.update_sources_platform_github
                                UpdateSourcePlatform.GITEA_FORGEJO -> R.string.update_sources_platform_gitea
                                UpdateSourcePlatform.CNB -> R.string.update_sources_platform_cnb
                                UpdateSourcePlatform.CNB_RAW -> R.string.update_sources_platform_cnb_raw
                            },
                            source.baseUrl,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${source.owner}/${source.repo}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!isDefault) {
                    Switch(
                        checked = source.enabled,
                        onCheckedChange = onEnabledChange,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pingSummary(ping),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ping?.error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onPing,
                    enabled = ping?.running != true,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.update_sources_ping))
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.update_sources_delete),
                        )
                    }
                }
            }

            FilterChip(
                selected = isPinned,
                onClick = onPin,
                label = {
                    Text(
                        stringResource(
                            if (isPinned) R.string.update_sources_unpin
                            else R.string.update_sources_pin
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Pin,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                shapes = FilterChipDefaults.shapes(),
            )
        }
    }
}

@Composable
private fun SourceBadge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun AddUpdateSourceDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> AddUpdateSourceResult,
) {
    val context = LocalContext.current
    var url by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val parsed = remember(url) {
        url.takeIf { it.isNotBlank() }?.let(::parseUpdateSourceUrl)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_sources_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.update_sources_url)) },
                    placeholder = { Text(stringResource(R.string.update_sources_url_placeholder)) },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (parsed != null) {
                    val host = parsed.baseUrl
                        .removePrefix("https://")
                        .removePrefix("http://")
                    Text(
                        text = stringResource(
                            when (parsed.platform) {
                                UpdateSourcePlatform.GITHUB -> R.string.update_sources_detected_github
                                UpdateSourcePlatform.GITEA_FORGEJO -> R.string.update_sources_detected_gitea
                                UpdateSourcePlatform.CNB -> R.string.update_sources_detected_cnb
                                UpdateSourcePlatform.CNB_RAW -> R.string.update_sources_detected_cnb_raw
                            },
                            "${parsed.owner}/${parsed.repo} · $host",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.update_sources_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (onAdd(url, name)) {
                        AddUpdateSourceResult.ADDED -> onDismiss()
                        AddUpdateSourceResult.INVALID_URL ->
                            errorMessage = context.getString(R.string.update_sources_invalid_url)

                        AddUpdateSourceResult.DUPLICATE ->
                            errorMessage = context.getString(R.string.update_sources_duplicate)

                        AddUpdateSourceResult.LIMIT_REACHED ->
                            errorMessage = context.getString(
                                R.string.update_sources_limit,
                                UpdateSourceDefaults.MAX_CUSTOM_SOURCES,
                            )
                    }
                },
                enabled = url.isNotBlank(),
            ) {
                Text(stringResource(R.string.update_sources_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_sources_cancel))
            }
        },
    )
}

@Composable
private fun pingSummary(ping: UpdateSourcesViewModel.PingState?): String = when {
    ping == null -> stringResource(R.string.update_sources_ping_idle)
    ping.running -> stringResource(R.string.update_sources_ping_running)
    ping.error != null -> stringResource(R.string.update_sources_ping_failed, ping.error)
    ping.apiLatencyMs != null && ping.downloadKbps != null ->
        stringResource(
            R.string.update_sources_ping_result,
            ping.apiLatencyMs,
            formatDownloadKbps(ping.downloadKbps),
        )

    ping.apiLatencyMs != null ->
        stringResource(R.string.update_sources_ping_api_only, ping.apiLatencyMs)

    else -> stringResource(R.string.update_sources_ping_idle)
}

private fun formatDownloadKbps(kbps: Long): String =
    if (kbps >= 1024) {
        String.format(Locale.US, "%.1f MB/s", kbps / 1024f)
    } else {
        "$kbps KB/s"
    }
