package com.gb6105.musictimely.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gb6105.musictimely.ui.theme.MtColors
import com.gb6105.musictimely.ui.theme.MtDimens
import com.gb6105.musictimely.ui.theme.MtType

/**
 * Design "Button/Primary": 58dp (grows with large text), radius 29, sunset gradient. Text uses ink for 4.5:1
 * contrast until the designer decides on white-on-sunset (docs/design/README.md, 미해결).
 */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(MtDimens.PrimaryButtonRadius)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val stops = PrimaryButtonStyle.stops(enabled, pressed)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MtDimens.PrimaryButtonHeight)
            .then(if (stops != null) Modifier.softShadow(shape, MtColors.Coral, MtShadows.AccentGlow) else Modifier)
            .clip(shape)
            .then(
                if (stops != null) Modifier.background(Brush.horizontalGradient(colorStops = stops), shape)
                else Modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape),
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = MtDimens.ScreenPadding, vertical = 12.dp),
    ) {
        Text(
            text,
            style = MtType.button,
            textAlign = TextAlign.Center,
            color = if (enabled) MtColors.Light.ink else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Secondary pill (342×46, r23 → 48dp touch). */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(MtDimens.SecondaryButtonRadius)
    val fill = MaterialTheme.colorScheme.surface
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = MtDimens.MinTouch)
            .softShadow(shape, fill, MtShadows.raised(isMtDark()))
            .clip(shape)
            .background(fill, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            style = MtType.button,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Design time chip: 70dp circle (larger when text is scaled up), number with an optional unit label ("MIN").
 * The selected one carries the sunset accent. [description] replaces the visible pieces for screen readers.
 */
@Composable
fun TimeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    description: String? = null,
) {
    val fill = MaterialTheme.colorScheme.surface
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .circleAtLeast(MtDimens.TimeChip)
            .then(
                if (selected) Modifier.softShadow(CircleShape, MtColors.Coral, MtShadows.ChipGlow)
                else Modifier.softShadow(CircleShape, fill, MtShadows.raised(isMtDark())),
            )
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(Brush.linearGradient(colorStops = MtColors.SunsetStops), CircleShape)
                else Modifier.background(fill, CircleShape),
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                description?.let { contentDescription = it }
            },
    ) {
        val ink = if (selected) MtColors.Light.ink else MaterialTheme.colorScheme.onSurface
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(6.dp).then(if (description != null) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            Text(label, style = MtType.chipNumber, color = ink)
            unit?.let { Text(it, style = MtType.unitLabel, color = ink) }
        }
    }
}

/** A circle of at least [min], growing to fit its content (text scaled to 200%) instead of clipping it. */
fun Modifier.circleAtLeast(min: Dp): Modifier = layout { measurable, constraints ->
    val width = measurable.maxIntrinsicWidth(Constraints.Infinity)
    val height = measurable.maxIntrinsicHeight(width)
    val side = maxOf(min.roundToPx(), width, height).coerceAtMost(constraints.maxWidth)
    val placeable = measurable.measure(Constraints.fixed(side, side))
    layout(side, side) { placeable.place(0, 0) }
}

/** Default·Pressed·Disabled. Pressed stops are read from the component sheet (coral → red); to confirm with design. */
object PrimaryButtonStyle {
    private val PressedStops = arrayOf(0f to MtColors.Coral, 1f to MtColors.Red)

    fun stops(enabled: Boolean, pressed: Boolean): Array<Pair<Float, Color>>? = when {
        !enabled -> null
        pressed -> PressedStops
        else -> MtColors.SunsetStops
    }
}
