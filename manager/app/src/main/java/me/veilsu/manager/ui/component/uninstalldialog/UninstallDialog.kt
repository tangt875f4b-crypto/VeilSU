package me.veilsu.manager.ui.component.uninstalldialog

import androidx.compose.runtime.Composable
import me.veilsu.manager.ui.LocalUiMode
import me.veilsu.manager.ui.UiMode

@Composable
fun UninstallDialog(
    show: Boolean,
    onDismissRequest: () -> Unit
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> UninstallDialogMiuix(show, onDismissRequest)
        UiMode.Material -> UninstallDialogMaterial(show, onDismissRequest)
    }
}
