package com.gb6105.musictimely.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gb6105.musictimely.domain.model.translation.ChipLayout
import com.gb6105.musictimely.ui.theme.MtColors
import com.gb6105.musictimely.ui.theme.MtDimens
import com.gb6105.musictimely.ui.theme.MtType

data class DialSegment(val startDegrees: Float, val sweepDegrees: Float)

/** One sunset segment per allocated song, at most [ChipLayout.MAX_VISUAL_CHIPS] like the chips (FR-A021). */
object DialSegments {
    const val GAP_DEGREES = 8f

    fun of(songs: Int?): List<DialSegment> {
        val count = (songs ?: 0).coerceIn(0, ChipLayout.MAX_VISUAL_CHIPS)
        if (count == 0) return emptyList()
        if (count == 1) return listOf(DialSegment(-90f, 360f))
        val sweep = (360f - count * GAP_DEGREES) / count
        return List(count) { i -> DialSegment(-90f + GAP_DEGREES / 2 + i * (sweep + GAP_DEGREES), sweep) }
    }
}

/** Ring track plus the song segments; a plain DrawScope function so it can be drawn off-screen in tests. */
internal fun DrawScope.drawDial(segments: List<DialSegment>, strokePx: Float, track: Color) {
    val diameter = size.minDimension - strokePx
    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
    drawCircle(track, radius = diameter / 2, center = center, style = Stroke(strokePx * 1.5f))
    // Figma dial: one diagonal sunset, orange at the bottom left to red at the top right (no seam).
    val brush = Brush.linearGradient(
        colorStops = MtColors.SunsetStops,
        start = Offset(topLeft.x, topLeft.y + diameter),
        end = Offset(topLeft.x + diameter, topLeft.y),
    )
    segments.forEach { segment ->
        drawArc(brush, segment.startDegrees, segment.sweepDegrees, useCenter = false, topLeft = topLeft, size = Size(diameter, diameter), style = Stroke(strokePx))
    }
}

/**
 * Design "분→곡 다이얼 카드" (350×188, r30): dial with the minutes inside, the song translation and its basis.
 * Screen readers get [description] (the SRS sentence, e.g. FR-A003 "25분이면 약 7곡이에요"); the dial is decoration.
 */
@Composable
fun TranslationCard(
    minutes: Int?,
    songs: Int?,
    minutesLine: String,
    amountLine: String,
    basisLine: String,
    unitLabel: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(MtDimens.CardRadius)
    val fill = MaterialTheme.colorScheme.surface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MtDimens.TranslationCardHeight)
            .softShadow(shape, fill, MtShadows.raised(isMtDark()))
            .background(fill, shape)
            .padding(MtDimens.CardPadding)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        val segments = DialSegments.of(songs)
        val track = MaterialTheme.colorScheme.background
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp).clearAndSetSemantics {}) {
            Canvas(Modifier.fillMaxSize()) { drawDial(segments, 14.dp.toPx(), track) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(minutes?.toString() ?: "–", style = MtType.dialNumber, color = MaterialTheme.colorScheme.onSurface)
                Text(unitLabel, style = MtType.unitLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(minutesLine, style = MtType.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Text(withNumerals(amountLine), style = MtType.songAmount, color = MaterialTheme.colorScheme.onSurface)
            Text(basisLine, style = MtType.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
