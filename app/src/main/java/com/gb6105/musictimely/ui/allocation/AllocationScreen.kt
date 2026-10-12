package com.gb6105.musictimely.ui.allocation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gb6105.musictimely.R
import com.gb6105.musictimely.domain.model.allocation.AllocationInput
import com.gb6105.musictimely.ui.component.CircleIcon
import com.gb6105.musictimely.ui.component.CircleIconButton
import com.gb6105.musictimely.ui.component.MusicAppRow
import com.gb6105.musictimely.ui.component.PrimaryButton
import com.gb6105.musictimely.ui.component.TaskField
import com.gb6105.musictimely.ui.component.TimeChip
import com.gb6105.musictimely.ui.component.TranslationCard
import com.gb6105.musictimely.ui.component.displayName
import com.gb6105.musictimely.ui.component.formatClock
import com.gb6105.musictimely.ui.theme.MtDimens
import com.gb6105.musictimely.ui.theme.MtType
import kotlinx.coroutines.launch

object AllocationTags {
    const val TITLE = "allocation_title"
    const val CUSTOM_MINUTES = "allocation_custom_minutes"
    const val START = "allocation_start"
    const val CUSTOM = "allocation_custom"

    fun preset(minutes: Int) = "allocation_preset_$minutes"
}

@Composable
fun AllocationRoute(
    viewModel: AllocationViewModel,
    onStarted: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenMusic: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    if (state.showNotificationPrompt) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.notification_prompt_title)) },
            text = { Text(stringResource(R.string.notification_prompt_body)) },
            confirmButton = {
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    scope.launch { if (viewModel.continueAfterNotificationPrompt()) onStarted() }
                }) { Text(stringResource(R.string.notification_prompt_continue)) }
            },
        )
    }
    AllocationScreen(
        state = state,
        onTitleChanged = viewModel::onTitleChanged,
        onPresetSelected = viewModel::onPresetSelected,
        onCustomMinutesChanged = viewModel::onCustomMinutesChanged,
        onStart = { scope.launch { if (viewModel.start()) onStarted() } },
        onStartOpenEnded = { scope.launch { if (viewModel.startOpenEnded()) onStarted() } },
        onOpenSettings = onOpenSettings,
        onOpenMusic = onOpenMusic,
        onMusicRow = { if (!viewModel.onMusicRow()) onOpenMusic() },
    )
}

/** SCR-01 / W01: task (optional) → time → song translation → start. No song-count prediction question. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllocationScreen(
    state: AllocationUiState,
    onTitleChanged: (String) -> Unit,
    onPresetSelected: (Int) -> Unit,
    onCustomMinutesChanged: (String) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenMusic: () -> Unit = {},
    onMusicRow: () -> Unit = onOpenMusic,
    onStartOpenEnded: () -> Unit = {},
) {
    var customOpen by rememberSaveable { mutableStateOf(state.selectedPreset == null) }
    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MtDimens.CardPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.allocation_screen_title),
                    style = MtType.screenTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                CircleIconButton(CircleIcon.GEAR, stringResource(R.string.settings_title), onOpenSettings)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.allocation_task_label),
                    style = MtType.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                TaskField(
                    value = state.titleInput,
                    onValueChange = onTitleChanged,
                    placeholder = stringResource(R.string.allocation_title_hint),
                    modifier = Modifier.testTag(AllocationTags.TITLE),
                )
            }
            Text(stringResource(R.string.allocation_question), style = MtType.sectionTitle, color = MaterialTheme.colorScheme.onSurface)
            FlowRow(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val unit = stringResource(R.string.allocation_unit_min)
                AllocationInput.PRESET_MINUTES.forEach { minutes ->
                    TimeChip(
                        label = minutes.toString(),
                        unit = unit,
                        description = stringResource(R.string.allocation_preset, minutes),
                        selected = state.selectedPreset == minutes,
                        onClick = {
                            customOpen = false
                            onPresetSelected(minutes)
                        },
                        modifier = Modifier.testTag(AllocationTags.preset(minutes)),
                    )
                }
                TimeChip(
                    label = "+",
                    unit = stringResource(R.string.allocation_custom_unit),
                    description = stringResource(R.string.allocation_custom_chip),
                    selected = state.selectedPreset == null,
                    onClick = { customOpen = true },
                    modifier = Modifier.testTag(AllocationTags.CUSTOM),
                )
            }
            if (customOpen || state.selectedPreset == null) {
                TaskField(
                    value = state.customInput,
                    onValueChange = onCustomMinutesChanged,
                    placeholder = stringResource(R.string.allocation_custom_label),
                    keyboardType = KeyboardType.Number,
                    isError = state.customError,
                    modifier = Modifier.testTag(AllocationTags.CUSTOM_MINUTES),
                )
                if (state.customError) {
                    Text(stringResource(R.string.allocation_custom_error), style = MtType.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            val minutesLine = state.minutes?.let { stringResource(R.string.translation_minutes, it) }
                ?: stringResource(R.string.allocation_start_disabled)
            val amountLine = state.songs?.let {
                stringResource(if (state.singleLoop) R.string.translation_loops else R.string.translation_songs, it)
            } ?: "–"
            val basisLine = if (state.singleLoop) {
                stringResource(R.string.translation_basis_loop, formatClock(state.unitMs))
            } else {
                stringResource(R.string.translation_basis_average, (state.unitMs / 60_000).toInt())
            }
            val sentence = if (state.minutes != null && state.songs != null && !state.singleLoop) {
                stringResource(R.string.allocation_translation, state.minutes, state.songs)
            } else {
                "$minutesLine $amountLine"
            }
            TranslationCard(
                minutes = state.minutes,
                songs = state.songs,
                minutesLine = minutesLine,
                amountLine = amountLine,
                basisLine = basisLine,
                unitLabel = stringResource(R.string.allocation_unit_min),
                description = "$sentence. $basisLine",
            )
            MusicAppRow(
                title = when (val music = state.music) {
                    AllocationMusic.Unset -> stringResource(R.string.allocation_pick_music)
                    is AllocationMusic.External -> music.label
                    is AllocationMusic.Noise -> stringResource(music.kind.displayName())
                    AllocationMusic.NoMusic -> stringResource(R.string.sheet_no_music)
                },
                subtitle = stringResource(
                    when (val music = state.music) {
                        AllocationMusic.Unset -> R.string.music_row_unset
                        is AllocationMusic.External -> if (music.counting) R.string.music_row_counting else R.string.music_row_off
                        is AllocationMusic.Noise -> R.string.music_row_noise
                        AllocationMusic.NoMusic -> R.string.music_row_none
                    },
                ),
                // A linked app opens itself; 바꾸기 keeps the choice reachable.
                onClick = onMusicRow,
                action = (state.music as? AllocationMusic.External)?.let {
                    {
                        TextButton(onClick = onOpenMusic, modifier = Modifier.heightIn(min = MtDimens.MinTouch)) {
                            Text(stringResource(R.string.music_row_change), style = MtType.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
            )
            if (state.launchNotice) {
                Text(stringResource(R.string.music_open_manually), style = MtType.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onStartOpenEnded, modifier = Modifier.fillMaxWidth().heightIn(min = MtDimens.MinTouch)) {
                Text(stringResource(R.string.allocation_open_ended), style = MtType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Pinned at the bottom like the design; stays above the keyboard through the safe-drawing insets.
        PrimaryButton(
            text = if (state.songs != null) {
                stringResource(R.string.allocation_start, state.songs)
            } else {
                stringResource(R.string.allocation_start_disabled)
            },
            onClick = onStart,
            enabled = state.canStart,
            modifier = Modifier
                .padding(horizontal = MtDimens.CardPadding, vertical = 12.dp)
                .testTag(AllocationTags.START),
        )
    }
}
