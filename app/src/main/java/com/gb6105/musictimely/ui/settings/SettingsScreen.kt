package com.gb6105.musictimely.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gb6105.musictimely.R
import com.gb6105.musictimely.domain.model.audio.NoiseSound
import com.gb6105.musictimely.ui.component.CircleIcon
import com.gb6105.musictimely.ui.component.CircleIconButton
import com.gb6105.musictimely.ui.component.MtShadows
import com.gb6105.musictimely.ui.component.formatClock
import com.gb6105.musictimely.ui.component.isMtDark
import com.gb6105.musictimely.ui.component.softShadow
import com.gb6105.musictimely.ui.component.withNumerals
import com.gb6105.musictimely.ui.theme.MtColors
import com.gb6105.musictimely.ui.theme.MtDimens
import com.gb6105.musictimely.ui.theme.MtRadius
import com.gb6105.musictimely.ui.theme.MtType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

object SettingsTags {
    const val COUNTING = "settings_counting"
    const val STOP_AT_END = "settings_stop_at_end"
    const val PAUSE_WITH_MUSIC = "settings_pause_with_music"
    const val VOLUME_UP = "settings_volume_up"
    const val VOLUME_DOWN = "settings_volume_down"
    const val FADE_IN_UP = "settings_fade_in_up"
    const val FADE_IN_DOWN = "settings_fade_in_down"
    const val FADE_OUT_UP = "settings_fade_out_up"
    const val FADE_OUT_DOWN = "settings_fade_out_down"
    const val SOUND_RESET = "settings_sound_reset"
    const val SINGLE_LOOP = "settings_single_loop"
    const val LOOP_LENGTH = "settings_loop_length"
    const val LOOP_APPLY = "settings_loop_apply"
    const val SHOW_SONGS = "settings_show_songs"
    const val VOLUME_SLIDER = "settings_volume_slider"
    const val AVERAGE_UP = "settings_average_up"
    const val AVERAGE_DOWN = "settings_average_down"
}

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onSeen: () -> Unit,
    enableCountingOnOpen: Boolean = false,
    onDataDeleted: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(enableCountingOnOpen) { if (enableCountingOnOpen) viewModel.setCounting(true) }
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    SettingsScreen(
        state = state,
        onBack = onBack,
        onSeen = onSeen,
        onCounting = { scope.launch { viewModel.setCounting(it) } },
        onStopAtEnd = { scope.launch { viewModel.setStopAtEnd(it) } },
        onPauseTimerWithMusic = { scope.launch { viewModel.setPauseTimerWithMusic(it) } },
        onShowSongs = { scope.launch { viewModel.setShowSongs(it) } },
        onAverage = { scope.launch { viewModel.setAverageMinutes(it) } },
        onSound = { change -> scope.launch { change(viewModel) } },
        onSingleLoop = { scope.launch { viewModel.setSingleLoop(it) } },
        onLoopLength = { text, done -> scope.launch { done(viewModel.setLoopLength(text)) } },
        onDeleteSource = { scope.launch { viewModel.deleteSource(it) } },
        onDeleteAll = {
            scope.launch {
                viewModel.deleteAllData()
                onDataDeleted()
            }
        },
    )
    if (state.showCountingPrompt) {
        CountingPrompt(
            onTurnOn = { scope.launch { viewModel.acceptCountingPrompt() } },
            onLater = { scope.launch { viewModel.declineCountingPrompt() } },
        )
    }
}

/**
 * SCR-09 in the Figma settings visual language (Light / Settings): small grey section headings over white
 * rounded cards, rows of title + grey subtitle + right control (sunset switch, value stepper, chevron).
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSeen: () -> Unit,
    onCounting: (Boolean) -> Unit,
    onStopAtEnd: (Boolean) -> Unit,
    onPauseTimerWithMusic: (Boolean) -> Unit = {},
    onShowSongs: (Boolean) -> Unit,
    onAverage: (Int) -> Unit,
    onDeleteSource: (String) -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
    onSound: (suspend (SettingsViewModel) -> Unit) -> Unit = {},
    onSingleLoop: (Boolean) -> Unit = {},
    onLoopLength: (String, (Boolean) -> Unit) -> Unit = { _, _ -> },
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MtDimens.CardPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircleIconButton(CircleIcon.BACK, stringResource(R.string.settings_back), onBack)
            Text(stringResource(R.string.settings_title), style = MtType.screenTitle, color = MaterialTheme.colorScheme.onSurface)
        }

        SectionHeading(stringResource(R.string.settings_section_counting))
        SettingsCard {
            SwitchRow(
                title = stringResource(R.string.settings_counting),
                description = if (state.countingOn && !state.accessGranted) {
                    stringResource(R.string.settings_counting_waiting)
                } else {
                    stringResource(R.string.settings_counting_desc)
                },
                checked = state.countingOn,
                enabled = true,
                tag = SettingsTags.COUNTING,
                onChange = onCounting,
            )
            CardDivider()
            SwitchRow(
                title = stringResource(R.string.settings_stop_at_end),
                description = if (state.stopAtEndEnabledInput) null else stringResource(R.string.settings_stop_at_end_reason),
                checked = state.stopAtEndOn && state.stopAtEndEnabledInput,
                enabled = state.stopAtEndEnabledInput,
                tag = SettingsTags.STOP_AT_END,
                onChange = onStopAtEnd,
            )
            CardDivider()
            // DEC-A17: off by default; only with song counting, like stop-at-end.
            SwitchRow(
                title = stringResource(R.string.settings_pause_with_music),
                description = stringResource(
                    if (state.stopAtEndEnabledInput) R.string.settings_pause_with_music_desc else R.string.settings_stop_at_end_reason,
                ),
                checked = state.pauseTimerWithMusicOn,
                enabled = state.stopAtEndEnabledInput,
                tag = SettingsTags.PAUSE_WITH_MUSIC,
                onChange = onPauseTimerWithMusic,
            )
        }
        if (state.showSystemPermissionGuide) Footnote(stringResource(R.string.settings_permission_guide))

        SectionHeading(stringResource(R.string.settings_section_display))
        SettingsCard {
            SwitchRow(
                stringResource(R.string.settings_show_songs), stringResource(R.string.settings_show_songs_desc),
                state.showSongs, true, SettingsTags.SHOW_SONGS, onShowSongs,
            )
            CardDivider()
            StepperRow(
                title = stringResource(R.string.settings_average_title),
                description = stringResource(R.string.settings_average_desc),
                value = stringResource(R.string.settings_minutes, state.averageMinutes),
                downTag = SettingsTags.AVERAGE_DOWN,
                upTag = SettingsTags.AVERAGE_UP,
                onDown = { onAverage(state.averageMinutes - 1) },
                onUp = { onAverage(state.averageMinutes + 1) },
            )
            CardDivider()
            LoopRows(state, onSingleLoop, onLoopLength)
        }

        SectionHeading(stringResource(R.string.settings_section_sound))
        SettingsCard {
            if (state.sources.isEmpty()) {
                SettingRow(stringResource(R.string.settings_sources), stringResource(R.string.settings_sources_empty)) {}
            } else {
                state.sources.forEachIndexed { index, source ->
                    if (index > 0) CardDivider()
                    SettingRow(source.label, if (index == 0) stringResource(R.string.settings_sources) else null) {
                        TextButton(onClick = { onDeleteSource(source.id) }, modifier = Modifier.heightIn(min = MtDimens.MinTouch)) {
                            Text(stringResource(R.string.settings_delete_source), style = MtType.bodySmall, color = MtColors.Icon)
                        }
                    }
                }
            }
        }
        SettingsCard { SoundRows(state.sound, onSound) }

        SectionHeading(stringResource(R.string.settings_section_privacy))
        SettingsCard {
            ChevronRow(stringResource(R.string.settings_seen), stringResource(R.string.settings_seen_desc), onClick = onSeen)
            CardDivider()
            ChevronRow(
                stringResource(R.string.settings_delete_all),
                stringResource(R.string.settings_delete_all_desc),
                titleColor = MtColors.Icon,
                onClick = { confirmDelete = true },
            )
        }
        Text(
            stringResource(R.string.settings_footer),
            style = MtType.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp),
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.settings_delete_all_confirm)) },
            text = { Text(stringResource(R.string.settings_delete_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteAll()
                }) { Text(stringResource(R.string.settings_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

/** SRS v1 §6.4: applies from the next session. Blank length uses the average song length. */
@Composable
private fun LoopRows(state: SettingsUiState, onSingleLoop: (Boolean) -> Unit, onLoopLength: (String, (Boolean) -> Unit) -> Unit) {
    var text by remember(state.loopDurationMs) { mutableStateOf(state.loopDurationMs?.let(::formatClock).orEmpty()) }
    var error by remember { mutableStateOf(false) }
    SwitchRow(stringResource(R.string.settings_single_loop), null, state.singleLoop, true, SettingsTags.SINGLE_LOOP, onSingleLoop)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = RowPadding, vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.settings_loop_length)) },
            isError = error,
            singleLine = true,
            shape = RoundedCornerShape(MtRadius.Small),
            modifier = Modifier.weight(1f).testTag(SettingsTags.LOOP_LENGTH),
        )
        TextButton(
            onClick = { onLoopLength(text) { ok -> error = !ok } },
            modifier = Modifier.heightIn(min = MtDimens.MinTouch).testTag(SettingsTags.LOOP_APPLY),
        ) {
            Text(stringResource(R.string.settings_loop_apply), style = MtType.body, color = MtColors.Icon)
        }
    }
    if (error) {
        Text(
            stringResource(R.string.settings_loop_error),
            style = MtType.caption,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = RowPadding, vertical = 4.dp),
        )
    }
}

/** FR-A070: built-in sound volume (slider, 10 % steps), fade-in (0.5 s steps), fade-out (1 s steps), reset. */
@Composable
private fun SoundRows(sound: NoiseSound, onSound: (suspend (SettingsViewModel) -> Unit) -> Unit) {
    val percent = (sound.volume * 100).roundToInt()
    val volumeTitle = stringResource(R.string.settings_volume_title)
    SettingRow(volumeTitle, stringResource(R.string.settings_volume_desc)) {
        Text(withNumerals(stringResource(R.string.settings_percent, percent)), style = MtType.body, color = MaterialTheme.colorScheme.onSurface)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
        StepButton("−", stringResource(R.string.settings_step_down, volumeTitle), SettingsTags.VOLUME_DOWN) {
            onSound { it.setNoiseVolume((percent - 10) / 100f) }
        }
        Slider(
            value = sound.volume,
            onValueChange = { value -> onSound { it.setNoiseVolume((value * 10).roundToInt() / 10f) } },
            valueRange = 0f..1f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = MtColors.Coral,
                activeTrackColor = MtColors.Coral,
                inactiveTrackColor = MaterialTheme.colorScheme.background,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier
                .weight(1f)
                .testTag(SettingsTags.VOLUME_SLIDER)
                .semantics { contentDescription = volumeTitle },
        )
        StepButton("+", stringResource(R.string.settings_step_up, volumeTitle), SettingsTags.VOLUME_UP) {
            onSound { it.setNoiseVolume((percent + 10) / 100f) }
        }
    }
    CardDivider()
    StepperRow(
        title = stringResource(R.string.settings_fade_in_title),
        description = null,
        value = stringResource(R.string.settings_seconds, "%.1f".format(sound.fadeInMs / 1_000f)),
        downTag = SettingsTags.FADE_IN_DOWN,
        upTag = SettingsTags.FADE_IN_UP,
        onDown = { onSound { it.setFadeInMs(sound.fadeInMs - 500) } },
        onUp = { onSound { it.setFadeInMs(sound.fadeInMs + 500) } },
    )
    CardDivider()
    StepperRow(
        title = stringResource(R.string.settings_fade_out_title),
        description = null,
        value = stringResource(R.string.settings_seconds, (sound.fadeOutMs / 1_000).toString()),
        downTag = SettingsTags.FADE_OUT_DOWN,
        upTag = SettingsTags.FADE_OUT_UP,
        onDown = { onSound { it.setFadeOutMs(sound.fadeOutMs - 1_000) } },
        onUp = { onSound { it.setFadeOutMs(sound.fadeOutMs + 1_000) } },
    )
    CardDivider()
    ChevronRow(stringResource(R.string.settings_sound_reset), null, tag = SettingsTags.SOUND_RESET, onClick = { onSound { it.resetSound() } })
}

private val RowPadding = 18.dp
private val SettingsCardRadius = 22.dp

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MtType.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 14.dp).semantics { heading() },
    )
}

/** White rounded card with the design's soft raised shadow. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(SettingsCardRadius)
    val fill = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(shape, fill, MtShadows.raised(isMtDark()))
            .background(fill, shape)
            .padding(vertical = 6.dp),
        content = content,
    )
}

@Composable
private fun CardDivider() {
    HorizontalDivider(Modifier.padding(horizontal = RowPadding), color = MaterialTheme.colorScheme.background)
}

@Composable
private fun Footnote(text: String) {
    Text(text, style = MtType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
}

/** Row = title + small grey subtitle + right control. */
@Composable
private fun SettingRow(
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = RowPadding, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MtType.body, color = titleColor)
            description?.let { Text(it, style = MtType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing()
    }
}

@Composable
private fun ChevronRow(title: String, description: String?, titleColor: Color = MaterialTheme.colorScheme.onSurface, tag: String? = null, onClick: () -> Unit) {
    SettingRow(
        title,
        description,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        titleColor = titleColor,
    ) {
        Icon(painterResource(R.drawable.ic_mt_chevron), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun StepperRow(
    title: String,
    description: String?,
    value: String,
    downTag: String,
    upTag: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
) {
    SettingRow(title, description) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", stringResource(R.string.settings_step_down, title), downTag, onDown)
            Text(withNumerals(value), style = MtType.body, color = MaterialTheme.colorScheme.onSurface)
            StepButton("+", stringResource(R.string.settings_step_up, title), upTag, onUp)
        }
    }
}

/** 48dp touch target around a small recessed circle. */
@Composable
private fun StepButton(symbol: String, label: String, tag: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(MtDimens.MinTouch)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.background, CircleShape),
        ) {
            Text(symbol, style = MtType.body, color = MtColors.Icon)
        }
    }
}

@Composable
private fun SwitchRow(title: String, description: String?, checked: Boolean, enabled: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    SettingRow(title, description) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MtColors.Coral,
                checkedThumbColor = Color.White,
                checkedBorderColor = Color.Transparent,
                uncheckedTrackColor = MaterialTheme.colorScheme.background,
            ),
            modifier = Modifier.testTag(tag),
        )
    }
}

/** SCR-11: explains what the app does and does not do before the system warning (DEC-A01, FR-A031). */
@Composable
fun CountingPrompt(onTurnOn: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.prompt_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.prompt_body))
                Text(stringResource(R.string.prompt_points))
            }
        },
        confirmButton = { TextButton(onClick = onTurnOn) { Text(stringResource(R.string.prompt_turn_on)) } },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.prompt_later)) } },
    )
}
