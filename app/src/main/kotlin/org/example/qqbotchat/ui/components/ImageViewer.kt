package org.example.qqbotchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import org.example.qqbotchat.R
import org.example.qqbotchat.ui.theme.TgTheme

@Composable
fun ImageViewer(url: String, onDismiss: () -> Unit) {
    val colors = TgTheme.colors
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(colors.mediaBackdrop).transformable(transform)) {
            AsyncImage(model = url, contentDescription = stringResource(R.string.photo), contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x.coerceIn(-size.width * (scale - 1) / 2, size.width * (scale - 1) / 2)
                    translationY = offset.y.coerceIn(-size.height * (scale - 1) / 2, size.height * (scale - 1) / 2)
                })
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close), tint = colors.unreadText)
            }
        }
    }
}
