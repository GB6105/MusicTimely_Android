package com.gb6105.musictimely.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gb6105.musictimely.R
import com.gb6105.musictimely.ui.theme.MtDimens
import com.gb6105.musictimely.ui.theme.MtType

/** Design "Actions / 원형 아이콘": back·gear·more·plus·pause·stop·play·close. */
enum class CircleIcon(@DrawableRes val drawable: Int) {
    BACK(R.drawable.ic_mt_back),
    GEAR(R.drawable.ic_mt_gear),
    MORE(R.drawable.ic_mt_more),
    PLUS(R.drawable.ic_mt_plus),
    PAUSE(R.drawable.ic_mt_pause),
    STOP(R.drawable.ic_mt_stop),
    PLAY(R.drawable.ic_mt_play),
    CLOSE(R.drawable.ic_mt_close),
}

/** 48dp circle (44pt raised, SRS Android §11) with a 24dp icon; [label] is read by screen readers. */
@Composable
fun CircleIconButton(icon: CircleIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    CircleDrawableButton(icon.drawable, label, onClick, modifier)
}

/** Same circle for icons outside the design set (e.g. chevron "open the music app"). */
@Composable
fun CircleDrawableButton(
    @DrawableRes drawable: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = MtDimens.CircleControl,
) {
    val fill = MaterialTheme.colorScheme.surface
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .softShadow(CircleShape, fill, MtShadows.raised(isMtDark()))
            .clip(CircleShape)
            .background(fill, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        // Icon colours come from the drawables (ink or accent.icon per design).
        Icon(painterResource(drawable), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
    }
}

/**
 * Session control (plus·stop 60, pause·play 84) with its caption below. The circle and caption are one button,
 * so the caption is both the visible and the spoken label.
 */
@Composable
fun SessionControl(icon: CircleIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = MtDimens.SessionControlSmall) {
    val fill = MaterialTheme.colorScheme.surface
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .widthIn(min = MtDimens.MinTouch)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .softShadow(CircleShape, fill, MtShadows.raised(isMtDark()))
                .background(fill, CircleShape),
        ) {
            Icon(painterResource(icon.drawable), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
        }
        Text(label, style = MtType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
