package me.veilsu.manager.ui.screen.susfs.component

import androidx.compose.runtime.Composable
import me.veilsu.manager.ui.LocalUiMode
import me.veilsu.manager.ui.UiMode
import me.veilsu.manager.ui.screen.susfs.component.miuix.AddPathDialogMiuix
import me.veilsu.manager.ui.screen.susfs.component.material.AddPathDialogMaterial

@Composable
fun AddPathDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isLoading: Boolean,
    titleRes: Int,
    labelRes: Int,
    initialValue: String = ""
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> AddPathDialogMiuix(
            showDialog = showDialog,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            isLoading = isLoading,
            titleRes = titleRes,
            labelRes = labelRes,
            initialValue = initialValue
        )
        UiMode.Material -> AddPathDialogMaterial(
            showDialog = showDialog,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            isLoading = isLoading,
            titleRes = titleRes,
            labelRes = labelRes,
            initialValue = initialValue
        )
    }
}
