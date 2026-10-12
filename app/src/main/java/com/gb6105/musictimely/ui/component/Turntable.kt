package com.gb6105.musictimely.ui.component

import androidx.compose.ui.graphics.drawscope.DrawScope
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gb6105.musictimely.R
import com.gb6105.musictimely.ui.session.MusicPanelState
import com.gb6105.musictimely.ui.theme.MtColors

/**
 * DEC-A12: the arm lowers and the record spins only while observed music plays (O0 later). Everything else —
 * paused, not counting, estimated, noise, no music — keeps the arm up and the record still, so the visual never
 * claims playback the app did not observe. Reduce Motion shows the final position without animation.
 */
data class TurntableState(val armDown: Boolean, val spinning: Boolean) {
    companion object {
        const val ARM_DOWN_DEGREES = 30f
        const val ARM_UP_DEGREES = 0f
        const val LOWER_MS = 650
        const val LIFT_MS = 550
        private const val SPIN_MS = 12_000

        fun of(panel: MusicPanelState): TurntableState {
            val playing = panel is MusicPanelState.Observed && panel.playing
            return TurntableState(armDown = playing, spinning = playing)
        }

        internal val spinMs get() = SPIN_MS
    }
}

/**
 * Record (diameter [size], 28 grooves, sunset label, spindle) with the tonearm beside it. The canvas is wider
 * than the record so the record stays centred while the arm rests outside it (pivot right of the record).
 * [label] is laid over the sunset label (A2 design) and stays upright while the record turns, so it stays readable.
 */
@Composable
fun Turntable(
    state: TurntableState,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    label: (@Composable BoxScope.() -> Unit)? = null,
) {
    val context = LocalContext.current
    val reducedMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val armAngle by animateFloatAsState(
        targetValue = if (state.armDown) TurntableState.ARM_DOWN_DEGREES else TurntableState.ARM_UP_DEGREES,
        animationSpec = tween(if (reducedMotion) 0 else if (state.armDown) TurntableState.LOWER_MS else TurntableState.LIFT_MS),
        label = "arm",
    )
    val spin = if (state.spinning && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "record")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(TurntableState.spinMs, easing = LinearEasing), RepeatMode.Restart),
            label = "spin",
        ).value
    } else {
        0f
    }
    val description = stringResource(if (state.spinning) R.string.record_spinning else R.string.record_still)
    Box(modifier.size(width = size * CANVAS_WIDTH_RATIO, height = size)) {
        Canvas(Modifier.size(width = size * CANVAS_WIDTH_RATIO, height = size).semantics { contentDescription = description }) {
            val radius = this.size.height / 2
            val center = Offset(this.size.width / 2, this.size.height / 2)
            drawVinylShadow(center, radius)
            rotate(spin, center) {
                drawCircle(MtColors.Vinyl, radius, center)
                val groove = 3.dp.toPx()
                for (i in 0 until GROOVES) {
                    drawCircle(Color.White.copy(alpha = 0.06f), radius * 0.97f - i * groove, center, style = Stroke(1f))
                }
                drawRecordLabel(center, radius * LABEL_RATIO)
                drawCircle(MtColors.Vinyl, 4.dp.toPx(), center)
            }
            // Sheen stays still while the record turns, like light on a real record.
            drawCircle(
                Brush.sweepGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent, Color.White.copy(alpha = 0.06f), Color.Transparent),
                    center,
                ),
                radius,
                center,
            )
            val pivot = Offset(center.x + radius * 1.04f, center.y - radius * 0.9f)
            rotate(armAngle, pivot) {
                val tip = Offset(pivot.x, pivot.y + radius * 1.35f)
                drawLine(Color(0xFFB4B5BE), pivot, tip, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                drawRoundRect(
                    Color(0xFF3A3B40),
                    topLeft = Offset(tip.x - 6.dp.toPx(), tip.y - 4.dp.toPx()),
                    size = Size(12.dp.toPx(), 18.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
                drawCircle(Color(0xFFD5D6DA), 12.dp.toPx(), pivot)
                drawCircle(Color(0xFF64656E), 5.dp.toPx(), pivot)
            }
        }
        if (label != null) Box(Modifier.align(Alignment.Center).size(size * LABEL_RATIO), content = label)
    }
}

private const val CANVAS_WIDTH_RATIO = 1.16f
private const val GROOVES = 28

/** A2: the label covers about 60 % of the record so it can carry a title. */
private const val LABEL_RATIO = 0.6f

/** Design "vinyl" shadow: a soft drop below the record. */
private fun DrawScope.drawVinylShadow(center: Offset, radius: Float) {
    drawIntoCanvas { canvas ->
        MtShadows.Vinyl.forEach { layer ->
            val paint = Paint().apply { color = MtColors.Vinyl }
            paint.asFrameworkPaint().apply {
                isAntiAlias = true
                setShadowLayer(layer.blur.toPx() / 2, layer.x.toPx(), layer.y.toPx(), layer.color.toArgb())
            }
            canvas.drawCircle(center, radius, paint)
        }
    }
}

/** Sunset record label: the gradient spans the label's own diameter, orange left to red right (review L3). */
internal fun DrawScope.drawRecordLabel(center: Offset, radius: Float) {
    val brush = Brush.horizontalGradient(colorStops = MtColors.SunsetStops, startX = center.x - radius, endX = center.x + radius)
    drawCircle(brush, radius, center)
}
