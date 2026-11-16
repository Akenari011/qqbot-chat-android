package org.example.qqbotchat.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.qqbotchat.R
import org.example.qqbotchat.ui.theme.TgTheme
import org.example.qqbotchat.util.AudioRecorder
import java.io.File
import java.util.UUID

data class ChatAttachments(
    val recording: Boolean, val loading: Boolean, val error: Int?,
    val pick: (String) -> Unit, val toggleRecording: () -> Unit,
    val cancelRecording: () -> Unit, val elapsedSeconds: () -> Long, val dismissError: () -> Unit,
)

@Composable
fun rememberChatAttachments(onSend: (ByteArray, String, Int, String?) -> Unit): ChatAttachments {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val send by rememberUpdatedState(onSend)
    val recorder = remember { AudioRecorder(context.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(owner, recorder) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { recorder.cancel(); recording = false }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); recorder.cancel() }
    }
    fun startRecording() {
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            recording = recorder.start()
            if (!recording) error = R.string.recording_failed
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else error = R.string.recording_denied
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            loading = true
            try {
                val attachment = withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: "attachment"
                    val mime = resolver.getType(uri).orEmpty()
                    val type = when { mime.startsWith("image/") -> 1; mime.startsWith("video/") -> 2; mime.startsWith("audio/") -> 3; else -> 4 }
                    val bytes = resolver.openInputStream(uri)?.use { input ->
                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) {
                            val count = input.read(chunk)
                            if (count < 0) break
                            require(buffer.size() + count <= MAX_ATTACHMENT_BYTES)
                            buffer.write(chunk, 0, count)
                        }
                        buffer.toByteArray()
                    } ?: error("No input stream")
                    require(bytes.isNotEmpty())
                    val localPath = if (type == 1) File(context.filesDir, "media").let { directory ->
                        directory.mkdirs()
                        File(directory, UUID.randomUUID().toString()).apply { writeBytes(bytes) }.absolutePath
                    } else null
                    LoadedAttachment(bytes, name, type, localPath)
                }
                send(attachment.bytes, attachment.name, attachment.type, attachment.path)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) { error = R.string.attachment_failed }
            finally { loading = false }
        }
    }
    return ChatAttachments(recording, loading, error,
        pick = { if (!loading) picker.launch(it) },
        toggleRecording = {
            if (recording) {
                val file = recorder.stop()
                recording = false
                if (file == null) error = R.string.recording_failed
                else scope.launch {
                    try {
                        val bytes = withContext(Dispatchers.IO) {
                            require(file.length() <= MAX_ATTACHMENT_BYTES)
                            file.readBytes()
                        }
                        send(bytes, file.name, 3, null)
                    } catch (_: Exception) { error = R.string.recording_failed }
                    finally { withContext(Dispatchers.IO) { file.delete() } }
                }
            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else permission.launch(Manifest.permission.RECORD_AUDIO)
        },
        cancelRecording = { recorder.cancel(); recording = false },
        elapsedSeconds = recorder::elapsedSeconds, dismissError = { error = null },
    )
}

private const val MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024
private data class LoadedAttachment(val bytes: ByteArray, val name: String, val type: Int, val path: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    TgActionSheet(onDismiss) { close ->
        Text(stringResource(R.string.attach), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        Text(stringResource(R.string.attachment_limit), color = TgTheme.colors.subtitle, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        val options = listOf(Triple(R.string.photo, R.drawable.ic_photo, "image/*"),
            Triple(R.string.video, R.drawable.ic_video, "video/*"), Triple(R.string.audio, R.drawable.ic_mic, "audio/*"),
            Triple(R.string.file, R.drawable.ic_file, "*/*"))
        options.forEach { (label, icon, mime) ->
            Row(Modifier.fillMaxWidth().clickable { close { onDismiss(); onPick(mime) } }.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), null, tint = TgTheme.colors.primary)
                Spacer(Modifier.width(24.dp))
                Text(stringResource(label))
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
