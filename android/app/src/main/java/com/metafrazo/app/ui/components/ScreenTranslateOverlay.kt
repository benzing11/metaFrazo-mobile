package com.metafrazo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
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

/**
 * Renders translated text directly over the original OCR'd block so the shared screen reads
 * as if the slide itself was translated. Each block's font size is derived from its box height
 * (scaled with the displayed video), the opaque background fully covers the original text, and
 * the text shrinks to fit instead of truncating.
 */
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

        // Letterbox math must match ParticipantTile's FitInside scale type.
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
            if (rw <= 0f || rh <= 0f) return@forEach

            val left = with(density) { (offX + rx * imgW).toDp() }
            val top = with(density) { (offY + ry * imgH).toDp() }
            var boxW = with(density) { (rw * imgW).toDp() }
            var boxH = with(density) { (rh * imgH).toDp() }

            // Translations usually run longer than the source; widen a touch (bounded by the
            // view) and grow height for multi-line content so the translated text fits.
            val shown = r.translated.ifBlank { r.text }
            val lines = estimateLines(shown)
            val padH = 4.dp
            val padV = 2.dp

            // Font sized to the block: box height minus vertical padding, in sp. Single-line
            // blocks fill their box; multi-line blocks split the height across lines.
            val heightPx = with(density) { boxH.toPx() }
            val padVPx = with(density) { (padV * 2).toPx() }
            val fontPx = ((heightPx - padVPx) / lines).coerceIn(with(density) { 9.sp.toPx() }, with(density) { 96.sp.toPx() })
            val fontSize = with(density) { fontPx.toDp().value.sp }

            // Grow the box for extra lines so longer translations don't get clipped, then clamp
            // to the remaining view space.
            val lineHeightPx = fontPx * 1.15f
            val neededHeightPx = lineHeightPx * lines + padVPx
            if (neededHeightPx > heightPx) {
                boxH = with(density) { neededHeightPx.toDp() }.coerceAtMost(maxHeight - top)
            }
            boxW = boxW.coerceAtMost(maxWidth - left)

            Box(
                modifier = Modifier
                    .offset(x = left, y = top)
                    .width(boxW)
                    .height(boxH)
                    // Opaque so the original text is fully hidden behind the translation.
                    .background(Color(0xFF101218))
                    .padding(horizontal = padH, vertical = padV),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = shown,
                    color = Color.White,
                    fontSize = fontSize,
                    lineHeight = with(density) { lineHeightPx.toDp().value.sp },
                    textAlign = TextAlign.Start,
                    maxLines = lines,
                    overflow = TextOverflow.Clip,
                    softWrap = true,
                )
            }
        }
    }
}

/** Rough line estimate from the translation text: explicit newlines plus a length heuristic. */
private fun estimateLines(text: String): Int {
    val explicit = text.count { it == '\n' } + 1
    // Long sentences wrap to a second line in a narrow OCR box.
    val wrapped = if (text.length > 40) explicit + 1 else explicit
    return wrapped.coerceIn(1, 4)
}
