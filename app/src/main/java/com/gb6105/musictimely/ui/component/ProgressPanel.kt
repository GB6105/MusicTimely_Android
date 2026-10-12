package com.gb6105.musictimely.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gb6105.musictimely.domain.model.translation.ChipLayout
import com.gb6105.musictimely.ui.theme.MtColors
import com.gb6105.musictimely.ui.theme.MtDimens

enum class ProgressCell { FILLED, CURRENT, EMPTY }

/** A2 cell colours: a light recessed track with mid-grey remaining cells. */
object ProgressCellStyle {
    val track: Color = Color(0xFFF1F1F3)
    val empty: Color = Color(0xFFD6D6DB)
}

/** Design "ProgressPanel" cells: passed songs filled, the current one outlined; last song outlines only the last cell. */
object ProgressCells {
    fun of(total: Int, filled: Int, lastSong: Boolean): List<ProgressCell> {
        if (ChipLayout.of(total, filled) !is ChipLayout.Chips) return emptyList()
        val passed = filled.coerceIn(0, total)
        val current = when {
            passed >= total -> -1
            lastSong -> total - 1
            else -> passed
        }
        return List(total) { i ->
            when {
                i == current -> ProgressCell.CURRENT
                i < passed -> ProgressCell.FILLED
                else -> ProgressCell.EMPTY
            }
        }
    }
}

/**
 * Cell bar inside its recessed track (306×20, r5): cells 38×10 (narrower when eight do not fit), gap 5, passed
 * cells sunset, current cell outline 1.6dp accent.icon (docs/design/README.md).
 */
@Composable
fun ProgressCellBar(cells: List<ProgressCell>, description: String, modifier: Modifier = Modifier) {
    val (maxCell, height) = MtDimens.ProgressCell
    val shape = RoundedCornerShape(MtDimens.ProgressTrackRadius)
    val sunset = Brush.horizontalGradient(colorStops = MtColors.SunsetStops)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(ProgressCellStyle.track, RoundedCornerShape(MtDimens.ProgressTrackHeight / 2))
            .padding(5.dp)
            .semantics { contentDescription = description },
    ) {
        val gaps = MtDimens.ProgressCellGap * (cells.size - 1).coerceAtLeast(0)
        val width = if (cells.isEmpty()) maxCell else minOf(maxCell, (maxWidth - gaps) / cells.size)
        Row(horizontalArrangement = Arrangement.spacedBy(MtDimens.ProgressCellGap)) {
            cells.forEach { cell ->
                val base = Modifier.size(width, height).testTag("progress_cell_${cell.name.lowercase()}")
                Box(
                    when (cell) {
                        ProgressCell.FILLED -> base.background(sunset, shape)
                        ProgressCell.CURRENT -> base.border(1.6.dp, MtColors.Icon, shape)
                        ProgressCell.EMPTY -> base.background(ProgressCellStyle.empty, shape)
                    },
                )
            }
        }
    }
}
