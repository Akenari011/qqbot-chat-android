package org.example.qqbotchat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.qqbotchat.R
import org.example.qqbotchat.ui.theme.TgTheme

@Composable
fun ChatSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String,
                    onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = TgTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            textStyle = TextStyle(color = c.name, fontSize = 17.sp),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f).focusRequester(focus),
            decorationBox = { inner ->
                Box { if (value.isEmpty()) Text(placeholder, color = c.subtitle, fontSize = 17.sp); inner() }
            },
        )
        IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close), tint = c.icon) }
    }
}

@Composable
fun ChatWallpaper(modifier: Modifier = Modifier) {
    val c = TgTheme.colors
    Canvas(modifier.background(Brush.linearGradient(listOf(c.wallpaper, c.wallpaperAccent)))) {
        val step = 72.dp.toPx()
        val stroke = Stroke(1.dp.toPx())
        for (row in 0..(size.height / step).toInt()) {
            for (column in 0..(size.width / step).toInt()) {
                val x = column * step + if (row % 2 == 0) 0f else step / 2
                val y = row * step
                drawCircle(c.primary.copy(alpha = .045f), 17.dp.toPx(), Offset(x, y), style = stroke)
                drawLine(c.primary.copy(alpha = .045f), Offset(x + 25, y + 27), Offset(x + 38, y + 40), strokeWidth = 2f)
            }
        }
    }
}

@Composable
fun ChatEmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    val c = TgTheme.colors
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(28.dp), color = c.primary.copy(alpha = .1f)) {
            Icon(painterResource(R.drawable.ic_chat), null, tint = c.primary, modifier = Modifier.padding(24.dp).size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.name)
        Spacer(Modifier.height(8.dp))
        Text(body, color = c.subtitle, style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
