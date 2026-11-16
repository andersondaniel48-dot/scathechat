package dev.daniel.scathechat.ui

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import dev.daniel.scathechat.data.Msg
import dev.daniel.scathechat.data.Repo
import dev.daniel.scathechat.media.MediaUtil
import java.io.File

@Composable
fun MediaViewer(m: Msg, onClose: () -> Unit) {
    val media = m.media ?: return
    val ctx = LocalContext.current
    val ready by Repo.ready.collectAsState()
    val progress by Repo.progress.collectAsState()
    val isReady = media.blob in ready
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(media.blob) {
        if (!isReady) failed = Repo.ensureMedia(media) == null
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val file = Repo.mediaFile(media)
            when {
                isReady && media.kind == "video" -> VideoPlayer(file)
                isReady -> ZoomImage(file)
                else -> {
                    val thumb = remember(media.thumb) { MediaUtil.decodeThumb(media.thumb) }
                    if (thumb != null) {
                        Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(), alpha = 0.5f)
                    }
                    Box(Modifier.align(Alignment.Center)) {
                        if (failed) Text("Couldn't download this. Check your connection.", color = Color.White)
                        else {
                            val p = progress[media.blob]
                            if (p != null) CircularProgressIndicator(progress = { p }, color = Color.White, modifier = Modifier.size(56.dp))
                            else CircularProgressIndicator(color = Color.White, modifier = Modifier.size(56.dp))
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().background(Color(0x66000000)).statusBarsPadding().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                if (isReady) {
                    IconButton(onClick = { MediaUtil.share(ctx, file, media) }) { Icon(Icons.Filled.Share, "Share", tint = Color.White) }
                    IconButton(onClick = { MediaUtil.saveToGallery(ctx, file, media) }) { Icon(Icons.Filled.Download, "Save", tint = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun ZoomImage(file: File) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = file,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f; offset = Offset.Zero
                    } else scale = 2.5f
                })
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale <= 1f) Offset.Zero else offset + pan
                }
            }
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
    )
}

@Composable
private fun VideoPlayer(file: File) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { c ->
                VideoView(c).apply {
                    val mc = MediaController(c)
                    mc.setAnchorView(this)
                    setMediaController(mc)
                    setVideoPath(file.path)
                    setOnPreparedListener { start(); mc.show(2500) }
                }
            },
            onRelease = { it.stopPlayback() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
