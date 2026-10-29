package com.metafrazo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
        val imgW = imageAspect * scale
        val imgH = 1f * scale
        val offX = (viewW - imgW) / 2f
        val offY = (viewH - imgH) / 2f
        regions.forEach { r ->
            val rx = r.x.coerceIn(0f, 1f)
            val ry = r.y.coerceIn(0f, 1f)
            val rw = r.w.coerceIn(0f, 1f - rx)
            val rh = r.h.coerceIn(0f, 1f - ry)
            val left = with(density) { (offX + (rx * imgW)).toDp() }
            val top = with(density) { (offY + (ry * imgH)).toDp() }
            val boxW = with(density) { (rw * imgW).toDp() }
            val boxH = with(density) { (rh * imgH).toDp() }
            Text(
                text = r.translated.ifBlank { r.text },
                modifier = Modifier
                    .offset(x = left, y = top)
                    .widthIn(min = 1.dp, max = if (boxW > 0.dp) boxW else maxWidth)
                    .heightIn(min = 1.dp, max = if (boxH > 0.dp) boxH else maxHeight)
                    .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
