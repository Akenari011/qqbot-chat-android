package org.example.qqbotchat.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import org.example.qqbotchat.ui.theme.TgTheme

/** Keep the sheet mounted through Material's hide animation before changing its owner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TgActionSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.((() -> Unit) -> Unit) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = TgTheme.colors.surface) {
        content { action ->
            if (!closing) {
                closing = true
                scope.launch {
                    try {
                        state.hide()
                        if (!state.isVisible) action()
                    } finally {
                        closing = false
                    }
                }
            }
        }
    }
}
