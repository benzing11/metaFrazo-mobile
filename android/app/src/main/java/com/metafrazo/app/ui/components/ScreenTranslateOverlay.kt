package com.metafrazo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class OverlayRegion(
    val text: String,
    val translated: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

@Composable
fun ScreenTranslateOverlay(
    regions: List<OverlayRegion>,
    imageAspect: Float = 16f / 9f,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        if (viewW <= 0 || viewH <= 0) return@BoxWithConstraints
        val scale = minOf(viewW / imageAspect, viewH / 1f)
        val imgWpx = imageAspect * scale
        val imgHpx = 1f * scale
        val offsetX = (viewW - imgWpx) / 2f
        val offsetY = (viewH - imgHpx) / 2f
        regions.forEach { r ->
            val left = with(density) { (offsetX + r.x * imgWpx).toDp() }
            val top = with(density) { (offsetY + r.y * imgHpx).toDp() }
            Text(
                text = r.translated.ifBlank { r.text },
                modifier = Modifier
                    .offset(x = left, y = top)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(2.dp),
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}