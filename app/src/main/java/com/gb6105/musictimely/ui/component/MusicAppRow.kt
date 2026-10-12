package com.gb6105.musictimely.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gb6105.musictimely.R
import com.gb6105.musictimely.ui.theme.MtDimens
import com.gb6105.musictimely.ui.theme.MtType

/** Design "음악 앱 행" (350×68, r34): note icon, title, subtitle, chevron; the whole row is one button. */
@Composable
fun MusicAppRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Trailing action (e.g. 바꾸기) shown instead of the chevron. */
    action: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(MtDimens.MusicAppRowRadius)
    val fill = MaterialTheme.colorScheme.surface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MtDimens.MusicAppRowHeight)
            .softShadow(shape, fill, MtShadows.raised(isMtDark()))
            .background(fill, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MtDimens.CardPadding, vertical = 12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_mt_note), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MtType.body, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MtType.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) action() else {
            Icon(painterResource(R.drawable.ic_mt_chevron), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(16.dp))
        }
    }
}
