package com.gb6105.musictimely.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gb6105.musictimely.R
import com.gb6105.musictimely.domain.model.playlist.Artwork
import com.gb6105.musictimely.domain.model.playlist.PlaylistEntry
import com.gb6105.musictimely.domain.model.result.TotalTime
import com.gb6105.musictimely.ui.component.CoverMotif
import com.gb6105.musictimely.ui.component.CoverTile
import com.gb6105.musictimely.ui.component.MtShadows
import com.gb6105.musictimely.ui.component.PillButton
import com.gb6105.musictimely.ui.component.PrimaryButton
import com.gb6105.musictimely.ui.component.isMtDark
import com.gb6105.musictimely.ui.component.softShadow
import com.gb6105.musictimely.ui.component.withNumerals
import com.gb6105.musictimely.ui.theme.MtColors
import com.gb6105.musictimely.ui.theme.MtDimens
import com.gb6105.musictimely.ui.theme.MtRadius
import com.gb6105.musictimely.ui.theme.MtType

/** SCR-05 / W04: facts only — no overrun rate, failure, praise or score (FR-A067). */
object ResultTags {
    const val PLAYLIST_ROW = "result_playlist_row"
    const val SHARE = "result_share"
    const val IDENTITY = "result_identity"
    const val COLLAGE_TILE = "result_collage_tile"
    fun cover(index: Int) = "result_cover_$index"
    fun coverImage(index: Int) = "result_cover_image_$index"
    fun collageImage(index: Int) = "result_collage_image_$index"
}

/**
 * SCR-05 / W04 as Figma "MT / Playlist / A Result" (DEC-A19, DEC-A20): title with the total time, the playlist
 * identity card, the songs directly on the background, the session memos (FR-A069) and the fixed [완료] /
 * [인스타로 공유하기] buttons. There is no close button: [완료] closes the result. Sharing (plan M5–M7) is not built
 * yet, so its button is shown disabled.
 */
@Composable
fun ResultScreen(
    state: ResultUiState,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
    onKeepMemos: () -> Unit = {},
    onDiscardMemos: () -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MtDimens.ScreenPadding)
                .padding(top = 22.dp, bottom = 16.dp),
        ) {
            PlaylistSection(state)
            if (state.memos.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                MemosCard(state.memos, onKeepMemos, onDiscardMemos)
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = MtDimens.ScreenPadding, vertical = 12.dp),
        ) {
            PrimaryButton(stringResource(R.string.result_next), onClick = onRestart)
            ShareButton()
        }
    }
}

/** Figma A outlined pill with the Instagram glyph. Disabled until plan M5–M7: no click, no intent. */
@Composable
private fun ShareButton() {
    val shape = RoundedCornerShape(MtDimens.SecondaryButtonRadius)
    val pending = stringResource(R.string.result_share_pending)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MtDimens.MinTouch)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .testTag(ResultTags.SHARE)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                disabled()
                stateDescription = pending
            }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_mt_instagram),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(stringResource(R.string.result_share), style = MtType.button, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * F18 M4 (DEC-A19, Figma "A / Playlist"): this session's counted songs in play order with the active total time.
 * Only confirmed songs are listed; with none the empty state takes the identity card's place (no list from the
 * average length).
 */
@Composable
private fun PlaylistSection(state: ResultUiState) {
    Text(stringResource(R.string.result_playlist_title), style = MtType.screenTitle, color = MaterialTheme.colorScheme.onSurface)
    Text(
        withNumerals(totalTimeText(state.totalTime)),
        style = MtType.bodySmall.copy(fontSize = 14.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 7.dp),
    )
    Spacer(Modifier.height(26.dp))
    if (state.playlist.isEmpty()) {
        CardShell(Modifier.padding(vertical = 8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                CoverTile(CoverMotif.RECORD, 56.dp, 12.dp)
                Text(
                    stringResource(R.string.result_playlist_empty),
                    style = MtType.body,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Note(stringResource(R.string.result_playlist_empty_note))
            }
        }
    } else {
        IdentityCard(state.playlist.take(CoverMotif.COLLAGE_TILES).map { it.artwork })
        Spacer(Modifier.height(25.dp))
        state.playlist.forEachIndexed { index, entry -> PlaylistRow(index, entry) }
        if (state.source == SongCountSource.MIXED) {
            Box(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) { Note(stringResource(R.string.result_playlist_partial)) }
        }
    }
}

/** White raised card (r30) shared by the identity card and the empty state. */
@Composable
private fun CardShell(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(MtDimens.CardRadius)
    val fill = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier
            .fillMaxWidth()
            .softShadow(shape, fill, MtShadows.raised(isMtDark()))
            .background(fill, shape)
            .padding(16.dp),
    ) { content() }
}

/** Figma "Playlist identity": 2×2 cover collage with "THIS SESSION" and the two-line title. */
@Composable
private fun IdentityCard(artworks: List<Artwork?>) {
    CardShell(Modifier.testTag(ResultTags.IDENTITY)) {
        Row(verticalAlignment = Alignment.Top) {
            Collage(artworks)
            Column(Modifier.padding(start = 19.dp, top = 9.dp)) {
                Text(
                    stringResource(R.string.result_identity_label),
                    style = MtType.unitLabel.copy(letterSpacing = 1.sp),
                    color = MtColors.Icon,
                )
                Text(
                    stringResource(R.string.result_identity_title),
                    style = MtType.panelTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 9.dp),
                )
            }
        }
    }
}

/** The first four songs' album art (DEC-A21); slots without one keep the motif. */
@Composable
private fun Collage(artworks: List<Artwork?>) {
    val gap = 3.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        CoverMotif.collage().chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                pair.forEachIndexed { col, motif ->
                    val slot = row * 2 + col
                    CoverTile(
                        artworks.getOrNull(slot), motif, 55.5.dp, 8.dp,
                        imageTag = ResultTags.collageImage(slot), motifTag = ResultTags.COLLAGE_TILE,
                        shifted = slot == CoverMotif.COLLAGE_TILES - 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(index: Int, entry: PlaylistEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 55.dp)
            .padding(horizontal = 4.dp)
            .testTag(ResultTags.PLAYLIST_ROW)
            .semantics(mergeDescendants = true) {},
    ) {
        CoverTile(
            entry.artwork, CoverMotif.variantFor(index), 40.dp, 6.dp,
            imageTag = ResultTags.coverImage(index), motifTag = ResultTags.cover(index),
            modifier = Modifier.padding(top = 6.dp),
        )
        Column(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp)) {
            Text(withNumerals(entry.title), style = MtType.body, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            entry.artist?.let {
                Text(withNumerals(it), style = MtType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun totalTimeText(t: TotalTime): String = when (t.shape) {
    TotalTime.Shape.SECONDS -> stringResource(R.string.result_total_time_s, t.seconds)
    TotalTime.Shape.MINUTES_SECONDS -> stringResource(R.string.result_total_time_ms, t.minutes, t.seconds)
    TotalTime.Shape.HOURS_MINUTES_SECONDS -> stringResource(R.string.result_total_time_hms, t.hours, t.minutes, t.seconds)
}

@Composable
private fun Note(text: String) = Text(text, style = MtType.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Saved thoughts card (field, r24): kept or deleted by the user, otherwise gone after 24 h (FR-A069). */
@Composable
private fun MemosCard(memos: List<String>, onKeep: () -> Unit, onDiscard: () -> Unit) {
    val shape = RoundedCornerShape(MtRadius.Medium)
    val fill = MaterialTheme.colorScheme.surfaceVariant
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(shape, fill, MtShadows.Field)
            .background(fill, shape)
            .padding(MtDimens.CardPadding),
    ) {
        Note(stringResource(R.string.result_memos, memos.size))
        memos.forEach { Text(it, style = MtType.body, color = MaterialTheme.colorScheme.onSurface) }
        Text(stringResource(R.string.result_memos_note), style = MtType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(stringResource(R.string.result_memos_keep), onClick = onKeep)
            TextButton(onClick = onDiscard, modifier = Modifier.heightIn(min = MtDimens.MinTouch)) {
                Text(stringResource(R.string.result_memos_discard), style = MtType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
