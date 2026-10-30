package com.metafrazo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.livekit.android.compose.ui.ScaleType
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack

/** One participant in the meeting grid: video when available, otherwise an initials avatar. */
@Composable
fun ParticipantTile(
    name: String,
    track: VideoTrack?,
    room: Room?,
    isSpeaking: Boolean,
    isMuted: Boolean,
    isLocal: Boolean,
    isScreenShare: Boolean,
    modifier: Modifier = Modifier,
    /**
     * Video scale type. Use [ScaleType.FitInside] for screen-share tiles so that translated
     * overlay regions (which are computed from the full video frame) line up with the video.
     * Defaults to [ScaleType.Fill] (centre-crop) to fill the camera grid without letterboxing.
     */
    scaleType: ScaleType = ScaleType.Fill,
) {
    val borderColor by animateColorAsState(
        if (isSpeaking) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "speaking",
    )
    Box(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(3.dp, borderColor, MaterialTheme.shapes.medium),
    ) {
        if (track != null) {
            VideoTrackView(
                videoTrack = track,
                modifier = Modifier.fillMaxSize(),
                passedRoom = room,
                scaleType = scaleType,
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.trim().take(1).uppercase().ifBlank { "?" },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(MaterialTheme.shapes.small)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildString {
                    append(if (isLocal) "You" else name)
                    if (isScreenShare) append(" · screen")
                },
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isMuted) {
                Icon(
                    Icons.Default.MicOff,
                    contentDescription = "Muted",
                    tint = Color.White,
                    modifier = Modifier.padding(start = 6.dp).size(14.dp),
                )
            }
        }
    }
}
