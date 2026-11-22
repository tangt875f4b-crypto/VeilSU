package me.veilsu.manager.ui.screen.susfs.content

import androidx.compose.runtime.Composable
import me.veilsu.manager.ui.LocalUiMode
import me.veilsu.manager.ui.UiMode
import me.veilsu.manager.ui.screen.susfs.content.miuix.SusMapsContentMiuix
import me.veilsu.manager.ui.screen.susfs.content.material.SusMapsContentMaterial

@Composable
fun SusMapsContent(
    susMaps: Set<String>,
    isLoading: Boolean,
    onAddSusMap: () -> Unit,
    onRemoveSusMap: (String) -> Unit,
    onEditSusMap: ((String) -> Unit)? = null,
    onReset: (() -> Unit)? = null
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> SusMapsContentMiuix(
            susMaps = susMaps,
            isLoading = isLoading,
            onAddSusMap = onAddSusMap,
            onRemoveSusMap = onRemoveSusMap,
            onEditSusMap = onEditSusMap,
            onReset = onReset
        )
        UiMode.Material -> SusMapsContentMaterial(
            susMaps = susMaps,
            isLoading = isLoading,
            onAddSusMap = onAddSusMap,
            onRemoveSusMap = onRemoveSusMap,
            onEditSusMap = { onEditSusMap?.invoke(it) },
            onReset = { onReset?.invoke() }
        )
    }
}
