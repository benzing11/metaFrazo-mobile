package com.metafrazo.app.ui.screens.recordings

import android.net.Uri
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.metafrazo.app.core.network.Api
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(recordingId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val url = "${Api.backendUrl}/api/recordings/$recordingId/stream"
    var isPlaying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val player = remember {
        val factory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(Api.authHeaders())
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(e: PlaybackException) { error = e.errorCodeName }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    var position by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(1f) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.toFloat()
            duration = player.duration.coerceAtLeast(1).toFloat()
            delay(500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recording") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), Arrangement.spacedBy(8.dp)) {
            AndroidView(
                factory = { ctx -> SurfaceView(ctx).apply { player.setVideoSurfaceView(this) } },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
            )
            error?.let { Text("Playback failed: $it", color = MaterialTheme.colorScheme.error) }
            Slider(value = position.coerceIn(0f, duration), onValueChange = { player.seekTo(it.toLong()) }, valueRange = 0f..duration)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(formatTime(position), style = MaterialTheme.typography.labelMedium)
                Button(onClick = { if (isPlaying) player.pause() else player.play() }) { Text(if (isPlaying) "Pause" else "Play") }
                Text(formatTime(duration), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun formatTime(ms: Float): String {
    val total = (ms / 1000).toLong().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
