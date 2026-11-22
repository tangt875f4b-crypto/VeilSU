package me.veilsu.manager.ui.screen.susfs.content

import androidx.compose.runtime.Composable
import me.veilsu.manager.ui.LocalUiMode
import me.veilsu.manager.ui.UiMode
import me.veilsu.manager.ui.screen.susfs.content.material.PriorityUidFilterContentMaterial
import me.veilsu.manager.ui.screen.susfs.content.miuix.PriorityUidFilterContentMiuix

@Composable
fun PriorityUidFilterContent(
    packages: Set<String>,
    isLoading: Boolean,
    onSelectApps: () -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Material -> PriorityUidFilterContentMaterial(packages, isLoading, onSelectApps, onApply, onClear)
        UiMode.Miuix -> PriorityUidFilterContentMiuix(packages, isLoading, onSelectApps, onApply, onClear)
    }
}
