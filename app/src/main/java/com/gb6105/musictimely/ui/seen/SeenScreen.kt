package com.gb6105.musictimely.ui.seen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gb6105.musictimely.R
import com.gb6105.musictimely.domain.model.observation.ObservationSnapshot
import com.gb6105.musictimely.domain.model.observation.SeenKind
import com.gb6105.musictimely.domain.repository.MusicControlPort
import com.gb6105.musictimely.ui.component.displayName
import com.gb6105.musictimely.ui.component.formatClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** SCR-13: only this session's in-memory log; empty once the session ends (FR-A040). */
@HiltViewModel
class SeenViewModel @Inject constructor(music: MusicControlPort) : ViewModel() {
    val snapshot: StateFlow<ObservationSnapshot> = music.snapshot
}

@Composable
fun SeenRoute(viewModel: SeenViewModel, onBack: () -> Unit) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    SeenScreen(snapshot, onBack)
}

@Composable
fun SeenScreen(snapshot: ObservationSnapshot, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.settings_back)) }
        Text(stringResource(R.string.seen_title), style = MaterialTheme.typography.titleLarge)
        val seen = snapshot.seen
        if (seen.isEmpty()) {
            Text(stringResource(R.string.seen_empty))
            return@Column
        }
        val start = seen.first().atMonoMs
        seen.lastOrNull { it.kind == SeenKind.CONNECTED }?.let {
            Text(stringResource(R.string.seen_app, stringResource(it.provider.displayName())))
        }
        seen.lastOrNull { it.kind == SeenKind.TRACK }?.let { track ->
            val title = track.title.orEmpty()
            val artist = track.artist
            Text(
                if (artist != null) stringResource(R.string.seen_track_artist, title, artist)
                else stringResource(R.string.seen_track, title),
            )
        }
        Text(stringResource(R.string.seen_counted, snapshot.countedItems))
        seen.filter { it.kind == SeenKind.COMMAND_PAUSE || it.kind == SeenKind.COMMAND_PLAY }.forEach { command ->
            val name = stringResource(if (command.kind == SeenKind.COMMAND_PLAY) R.string.seen_command_play else R.string.seen_command_pause)
            Text(stringResource(R.string.seen_command, name, formatClock(command.atMonoMs - start)))
        }
        Text(stringResource(R.string.seen_note), style = MaterialTheme.typography.bodySmall)
    }
}
