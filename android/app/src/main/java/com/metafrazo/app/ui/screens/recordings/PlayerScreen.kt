package com.metafrazo.app.ui.screens.recordings

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.view.SurfaceView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun PlayerScreen(recordingId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val url = "http://10.0.2.2:3000/api/recordings/$recordingId/stream"
    val player = remember {
        val factory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(mapOf("X-Dev-User-Id" to "dev-user"))
        ExoPlayer.Builder(context).setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(factory)).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
        }
    }
    var position by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(1f) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.toFloat()
            duration = player.duration.coerceAtLeast(1).toFloat()
            kotlinx.coroutines.delay(500)
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    Column(Modifier.fillMaxSize().padding(12.dp), Arrangement.spacedBy(8.dp)) {
        Text("Player", style = MaterialTheme.typography.headlineLarge)
        AndroidView(factory = { ctx ->
            SurfaceView(ctx).apply { player.setVideoSurfaceView(this) }
        }, modifier = Modifier.fillMaxWidth().weight(1f))
        Slider(value = position, onValueChange = { player.seekTo(it.toLong()) }, valueRange = 0f..duration)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (player.isPlaying) player.pause() else player.play() }) { Text(if (player.isPlaying) "Pause" else "Play") }
            Button(onClick = onBack) { Text("Back") }
        }
    }
}