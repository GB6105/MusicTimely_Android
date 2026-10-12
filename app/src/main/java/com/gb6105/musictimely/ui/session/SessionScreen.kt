package com.gb6105.musictimely.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.gb6105.musictimely.R
import com.gb6105.musictimely.domain.model.timer.ExtensionStep
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.model.translation.ChipLayout
import com.gb6105.musictimely.domain.model.translation.CueKind
import com.gb6105.musictimely.ui.component.CircleDrawableButton
import com.gb6105.musictimely.ui.component.CircleIcon
import com.gb6105.musictimely.ui.component.CircleIconButton
import com.gb6105.musictimely.ui.component.MtShadows
import com.gb6105.musictimely.ui.component.PillButton
import com.gb6105.musictimely.ui.component.ProgressCellBar
import com.gb6105.musictimely.ui.component.ProgressCells
import com.gb6105.musictimely.ui.component.SessionControl
import com.gb6105.musictimely.ui.component.TaskField
import com.gb6105.musictimely.ui.component.Turntable
import com.gb6105.musictimely.ui.component.TurntableState
import com.gb6105.musictimely.ui.component.displayName
import com.gb6105.musictimely.ui.component.formatClock
import com.gb6105.musictimely.ui.component.formatTenths
import com.gb6105.musictimely.ui.component.isMtDark
import com.gb6105.musictimely.ui.component.softShadow
import com.gb6105.musictimely.ui.component.withNumerals
import com.gb6105.musictimely.ui.theme.MtColors
import com.gb6105.musictimely.ui.theme.MtDimens
import com.gb6105.musictimely.ui.theme.MtRadius
import com.gb6105.musictimely.ui.theme.MtType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SessionTags {
    const val RECORD_LABEL = "session_record_label"
    const val CUE_LINE = "session_cue_line"
}

@Composable
fun SessionRoute(
    viewModel: SessionViewModel,
    onFinished: (sessionId: String) -> Unit,
    tickIntervalMs: Long = 1_000,
    onEnableCounting: () -> Unit = {},
    onOpenMusic: () -> Unit = {},
    onOpenMemo: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        // UI reads derived values at most 1 Hz and only while visible (NFR-A006, review MEDIUM-16).
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refresh()
                delay(tickIntervalMs)
            }
        }
    }
    val current = state
    if (current is SessionUiState.Finished) {
        // Finished elsewhere (notification [마침], recovery) still moves on to the result (review HIGH-7).
        LaunchedEffect(current) { onFinished(current.session.sessionId) }
        return
    }
    val active = current as? SessionUiState.Active ?: return
    if (active.askStopOtherMusic) {
        AlertDialog(
            onDismissRequest = { viewModel.answerStopOtherMusic(stop = false) },
            title = { Text(stringResource(R.string.single_source_question)) },
            confirmButton = { TextButton(onClick = { viewModel.answerStopOtherMusic(stop = true) }) { Text(stringResource(R.string.single_source_stop)) } },
            dismissButton = { TextButton(onClick = { viewModel.answerStopOtherMusic(stop = false) }) { Text(stringResource(R.string.single_source_keep)) } },
        )
    }
    SessionScreen(
        state = active,
        onPause = { scope.launch { viewModel.pause() } },
        onResume = { scope.launch { viewModel.resume() } },
        onExtend = { step -> scope.launch { viewModel.extend(step) } },
        onFinish = { scope.launch { viewModel.finish() } },
        onMusicButton = viewModel::onMusicButton,
        onOpenMusic = viewModel::openMusic,
        onEnableCounting = onEnableCounting,
        onChangeMusic = onOpenMusic,
        onMemo = onOpenMemo,
        onNoiseButton = viewModel::onNoiseButton,
        onStopNow = viewModel::stopNow,
        onKeepListening = viewModel::keepListening,
        onCorrect = { viewModel.correctSong(it) },
        onOpenSettings = onOpenSettings,
    )
}

/**
 * SCR-02 running, SCR-03 paused, SCR-04 limit reached (Figma Gui 4:480·4:1224·4:1045·4:1123).
 * Timer controls never command external music; the music button stays a separate, meaning-labelled control.
 */
@Composable
fun SessionScreen(
    state: SessionUiState.Active,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onExtend: (ExtensionStep) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    onMusicButton: () -> Unit = {},
    onOpenMusic: () -> Unit = {},
    onEnableCounting: () -> Unit = {},
    onChangeMusic: () -> Unit = {},
    onMemo: () -> Unit = {},
    onNoiseButton: () -> Unit = {},
    onStopNow: () -> Unit = {},
    onKeepListening: () -> Unit = {},
    onCorrect: (Int) -> Boolean = { false },
    onOpenSettings: () -> Unit = {},
) {
    var correcting by rememberSaveable { mutableStateOf(false) }
    val canCorrect = state.showSongs && state.canCorrect && state.totalSongs != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MtDimens.CardPadding, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TopBar(
            title = state.taskTitle ?: stringResource(R.string.default_task),
            canCorrect = canCorrect,
            onChangeMusic = onChangeMusic,
            onCorrect = { correcting = true },
            onOpenSettings = onOpenSettings,
        )
        RecordCaption(state, onMusicButton)
        Record(state)
        TimeCard(state)
        CueLine(state)
        MusicRow(state, onOpenMusic, onEnableCounting, onChangeMusic, onNoiseButton)
        CountedLine(state)
        Notices(state)
        if (correcting && canCorrect) CorrectSong(onCorrect, onDone = { correcting = false })
        if (state.showSongs && state.correctionNotice) {
            Text(stringResource(R.string.session_correction_done), style = MtType.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.phase == TimerState.LIMIT_REACHED) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtensionStep.entries.forEach { step ->
                    PillButton(stringResource(R.string.action_extend, (step.addedMs / 60_000).toInt()), onClick = { onExtend(step) })
                }
            }
            StopAtEndSection(state.stopNotice, onStopNow, onKeepListening)
        }
        Controls(state.phase, onMemo, onPause, onResume, onFinish)
    }
}

/** Centred "집중 세션" caption and task (two lines, ellipsis; FR-A001) with the more menu. No back: the flow has none. */
@Composable
private fun TopBar(title: String, canCorrect: Boolean, onChangeMusic: () -> Unit, onCorrect: () -> Unit, onOpenSettings: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.size(MtDimens.CircleControl))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(stringResource(R.string.session_caption), style = MtType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                title,
                style = MtType.body,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            CircleIconButton(CircleIcon.MORE, stringResource(R.string.session_more), onClick = { menuOpen = true })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.music_change)) }, onClick = {
                    menuOpen = false
                    onChangeMusic()
                })
                if (canCorrect) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.session_correct)) }, onClick = {
                        menuOpen = false
                        onCorrect()
                    })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_title)) }, onClick = {
                    menuOpen = false
                    onOpenSettings()
                })
            }
        }
    }
}

/**
 * A2 small grey line above the record, with the user's music button at its right end only while songs are
 * observed (DEC-A07). Paused: the SCR-03 title (DEC-A16) and, without counting, the D0 pause copy.
 */
@Composable
private fun RecordCaption(state: SessionUiState.Active, onMusicButton: () -> Unit) {
    val music = state.music
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.size(MtDimens.CircleControl))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            caption(state)?.let {
                Text(it, style = MtType.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            if (state.phase == TimerState.PAUSED && music.showsD0PauseCopy) {
                Text(
                    stringResource(R.string.session_paused_music_d0),
                    style = MtType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (music is MusicPanelState.Observed) {
            CircleDrawableButton(
                drawable = when (music.button) {
                    MusicButton.PAUSE -> R.drawable.ic_mt_pause
                    MusicButton.PLAY -> R.drawable.ic_mt_play
                    MusicButton.OPEN -> R.drawable.ic_mt_chevron
                },
                label = stringResource(
                    when (music.button) {
                        MusicButton.PAUSE -> R.string.music_button_pause
                        MusicButton.PLAY -> R.string.music_button_play
                        MusicButton.OPEN -> R.string.music_button_open_app
                    },
                ),
                onClick = onMusicButton,
            )
        } else {
            Spacer(Modifier.size(MtDimens.CircleControl))
        }
    }
}

/** Limit and pause titles first; observed lines never claim listening once the music has stopped (FR-A018). */
@Composable
private fun caption(state: SessionUiState.Active): String? {
    val music = state.music
    return when {
        state.phase == TimerState.LIMIT_REACHED -> stringResource(R.string.session_limit_reached)
        state.phase == TimerState.PAUSED -> {
            // DEC-A16: observed music that has stopped rests with the timer; otherwise only the timer does.
            val musicRests = (music as? MusicPanelState.Observed)?.playing == false
            stringResource(if (musicRests) R.string.session_paused_with_music else R.string.session_paused_title)
        }
        music is MusicPanelState.Observed -> when {
            music.title == null -> stringResource(R.string.music_observing)
            !music.playing -> stringResource(R.string.session_caption_music_paused)
            music.handoffProvider != null -> stringResource(R.string.session_caption_handoff, stringResource(music.handoffProvider.displayName()))
            else -> stringResource(R.string.session_caption_listening)
        }
        state.showSongs && state.observedCount == null -> stringResource(R.string.session_estimated_note)
        else -> null
    }
}

/**
 * Record and arm (DEC-A12) with the A2 label text. The title appears only while songs are observed (FR-A018,
 * title only — no artist is kept); estimating shows "약 N번째 곡" and never a song name.
 */
@Composable
private fun Record(state: SessionUiState.Active) {
    val music = state.music
    val observed = music as? MusicPanelState.Observed
    val center = when {
        observed != null -> observed.title
        state.showSongs && state.observedCount == null && state.phase != TimerState.LIMIT_REACHED ->
            stringResource(if (state.singleLoop) R.string.session_estimated_loop else R.string.session_estimated_song, state.currentSong)
        else -> null
    }
    BoxWithConstraints(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        val size = minOf(MtDimens.Vinyl, maxWidth / 1.16f)
        Turntable(TurntableState.of(music), size = size) {
            RecordLabel(nowPlaying = observed?.playing == true && observed.title != null, center = center)
        }
    }
}

/** Label text: dark ink on sunset (contrast over 4.5:1), shrinking to fit the label instead of overflowing. */
@Composable
private fun BoxScope.RecordLabel(nowPlaying: Boolean, center: String?) {
    val ink = MtColors.Vinyl
    val tiny = MtType.unitLabel.copy(color = ink, textAlign = TextAlign.Center)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .matchParentSize()
            .testTag(SessionTags.RECORD_LABEL)
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (nowPlaying) {
                    BasicText(
                        stringResource(R.string.record_label_now_playing),
                        style = tiny,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 9.sp),
                    )
                }
                center?.let {
                    BasicText(
                        withNumerals(it),
                        style = MtType.body.copy(color = ink, textAlign = TextAlign.Center),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 20.sp),
                    )
                }
            }
        }
        // Room for the spindle, centred: equal weights above and below keep the title off the spindle.
        Spacer(Modifier.height(22.dp))
        Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.weight(1f).fillMaxWidth()) {
            BasicText(
                stringResource(R.string.record_label_side),
                style = tiny,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 9.sp),
            )
        }
    }
}

/**
 * A2 time card (white, r26): elapsed / remaining clocks, "곡 진행" on the right, cells below. Screen readers get
 * the full SRS sentence (remaining amount or cue, then the elapsed line) as the card's description.
 */
@Composable
private fun TimeCard(state: SessionUiState.Active) {
    val shape = RoundedCornerShape(MtDimens.PanelRadius)
    val fill = MaterialTheme.colorScheme.surfaceVariant
    val description = stringResource(R.string.session_time_card_a11y, headline(state).orEmpty(), elapsedLine(state)).removePrefix(", ")
    val progress = SongProgress.of(state)
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(shape, fill, MtShadows.raised(isMtDark()))
            .background(fill, shape)
            .semantics { contentDescription = description }
            .padding(MtDimens.CardPadding),
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            TimeFigure(stringResource(R.string.session_time_elapsed), formatClock(state.elapsedMs), Modifier.weight(1f))
            state.remainingMs?.let { remaining ->
                Text("/", style = MtType.chipNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
                TimeFigure(stringResource(R.string.session_time_remaining), formatClock(remaining), Modifier.weight(1f))
            }
            if (progress != null) {
                VerticalDivider(Modifier.padding(horizontal = 12.dp).height(44.dp), color = MaterialTheme.colorScheme.background)
                val total = progress.total
                TimeFigure(
                    label = stringResource(if (progress.loops) R.string.session_loop_progress else R.string.session_song_progress),
                    value = when {
                        total == null && progress.estimated -> stringResource(R.string.session_progress_estimated_open, progress.current)
                        total == null -> stringResource(R.string.session_progress_exact_open, progress.current)
                        progress.estimated -> stringResource(R.string.session_progress_estimated, progress.current, total)
                        else -> stringResource(R.string.session_progress_exact, progress.current, total)
                    },
                    modifier = Modifier.weight(0.9f),
                )
            }
        }
        if (state.showSongs) SongChips(state)
    }
}

/** Small grey label over a large Montserrat figure that shrinks rather than wraps (font 200 %). */
@Composable
private fun TimeFigure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MtType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(
            withNumerals(value),
            style = MtType.timeCard.copy(color = MaterialTheme.colorScheme.onSurface),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = MtType.timeCard.fontSize),
        )
    }
}

/** The last-song cue (FR-A017; plain when song display is off) as a short line under the card. */
@Composable
private fun CueLine(state: SessionUiState.Active) {
    if (state.cue == null || state.phase == TimerState.LIMIT_REACHED) return
    Text(
        withNumerals(headline(state).orEmpty()),
        style = MtType.body,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(SessionTags.CUE_LINE),
    )
}

/** Observed count, moved from the old panel: "N곡 지났어요 (곡 세기 시작 후)". */
@Composable
private fun CountedLine(state: SessionUiState.Active) {
    val count = state.observedCount?.takeIf { state.showSongs } ?: return
    RowNote(stringResource(if (state.observedSinceMidSession) R.string.music_counted_since else R.string.music_counted, count))
}

/** Compact music panel row for the non-observed states; texts and actions as SRS Android §3.1. */
@Composable
private fun MusicRow(
    state: SessionUiState.Active,
    onOpenMusic: () -> Unit,
    onEnableCounting: () -> Unit,
    onPick: () -> Unit,
    onNoiseButton: () -> Unit,
) {
    if (state.music is MusicPanelState.Observed) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            when (val music = state.music) {
                MusicPanelState.Empty -> RowText(stringResource(R.string.music_pick_app))
                is MusicPanelState.ExternalOff -> {
                    RowText(stringResource(R.string.music_average_basis, music.label, state.averageSongMinutes))
                    TextButton(onClick = onEnableCounting, modifier = Modifier.heightIn(min = MtDimens.MinTouch)) {
                        Text(stringResource(R.string.music_enable_counting), style = MtType.bodySmall, color = MtColors.Icon)
                    }
                }
                is MusicPanelState.ExternalWaiting -> RowText(music.label)
                is MusicPanelState.Noise -> {
                    RowText(stringResource(music.kind.displayName()))
                    if (music.unavailable) RowNote(stringResource(R.string.noise_unavailable))
                    if (music.muted) RowNote(stringResource(R.string.noise_muted))
                }
                MusicPanelState.None -> RowText(stringResource(R.string.sheet_no_music))
                is MusicPanelState.Observed -> Unit
            }
        }
        when (val music = state.music) {
            MusicPanelState.Empty -> PillButton(stringResource(R.string.music_pick), onClick = onPick)
            is MusicPanelState.ExternalOff, is MusicPanelState.ExternalWaiting -> PillButton(stringResource(R.string.music_open), onClick = onOpenMusic)
            is MusicPanelState.Noise -> CircleDrawableButton(
                drawable = if (music.playing) R.drawable.ic_mt_stop else R.drawable.ic_mt_play,
                label = stringResource(if (music.playing) R.string.noise_stop else R.string.noise_play),
                onClick = onNoiseButton,
            )
            MusicPanelState.None -> PillButton(stringResource(R.string.music_change_short), onClick = onPick)
            is MusicPanelState.Observed -> Unit
        }
    }
}

@Composable
private fun RowText(text: String) = Text(text, style = MtType.body, color = MaterialTheme.colorScheme.onSurface)

@Composable
private fun RowNote(text: String) = Text(text, style = MtType.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Limit, the last-song cue (FR-A017; plain when song display is off), then the existing remaining line. */
@Composable
private fun headline(state: SessionUiState.Active): String? {
    val remaining = state.remainingSongs
    val cue = state.cue
    return when {
        state.phase == TimerState.LIMIT_REACHED -> stringResource(R.string.session_limit_reached)
        cue != null -> stringResource(
            when {
                !state.showSongs -> R.string.session_cue_plain
                cue == CueKind.ABOUT_ONE_SONG_LEFT -> R.string.session_cue_about_one_song
                else -> R.string.session_cue_this_song
            },
        )
        // FR-A020: song display off keeps the numeric timer only (open-ended: the elapsed line alone).
        !state.showSongs -> state.remainingMs?.let { stringResource(R.string.session_remaining_time, formatClock(it)) }
        remaining != null ->
            stringResource(if (state.singleLoop) R.string.session_remaining_loops else R.string.session_remaining_songs, remaining)
        else -> stringResource(R.string.session_elapsed_amount, formatTenths(state.elapsedTenths))
    }
}

/** Plus "생각 메모", centre pause/play (84dp), stop "마치기". At the limit the centre control gives way to extensions. */
@Composable
private fun Controls(phase: TimerState, onMemo: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onFinish: () -> Unit) {
    val smallOffset = (MtDimens.SessionControlLarge - MtDimens.SessionControlSmall) / 2
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        SessionControl(CircleIcon.PLUS, stringResource(R.string.session_memo), onMemo, Modifier.padding(top = smallOffset))
        when (phase) {
            TimerState.PAUSED ->
                SessionControl(CircleIcon.PLAY, stringResource(R.string.session_control_resume), onResume, size = MtDimens.SessionControlLarge)
            TimerState.LIMIT_REACHED -> Unit
            else -> SessionControl(CircleIcon.PAUSE, stringResource(R.string.session_control_pause), onPause, size = MtDimens.SessionControlLarge)
        }
        SessionControl(CircleIcon.STOP, stringResource(R.string.action_finish), onFinish, Modifier.padding(top = smallOffset))
    }
}

@Composable
private fun elapsedLine(state: SessionUiState.Active): String {
    val target = state.remainingMs?.let { state.elapsedMs + it }
    return if (target != null) {
        stringResource(R.string.session_elapsed_of_target, formatClock(state.elapsedMs), (target / 60_000).toInt())
    } else {
        stringResource(R.string.session_elapsed, formatClock(state.elapsedMs))
    }
}

/** Remaining-song cells; more than eight songs show the summary line only (FR-A021). */
@Composable
private fun SongChips(state: SessionUiState.Active) {
    val total = state.totalSongs ?: return
    // A2: the cells follow the "곡 진행" number next to them (passed = current − 1), not the clock alone.
    val passed = SongProgress.of(state)?.let { it.current - 1 } ?: state.filledChips
    val description = stringResource(R.string.session_chips_a11y, total, passed)
    when (ChipLayout.of(total, passed)) {
        // Design ProgressPanel: cells per song; the last-song cue outlines only the last cell.
        is ChipLayout.Chips -> ProgressCellBar(ProgressCells.of(total, passed, lastSong = state.cue != null), description)
        is ChipLayout.Summary -> Text(
            text = stringResource(R.string.session_chips_summary, total),
            style = MtType.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@Composable
private fun Notices(state: SessionUiState.Active) {
    if (state.musicStoppedHint) RowNote(stringResource(R.string.music_stopped_hint))
    if (state.controlNotice) RowNote(stringResource(R.string.music_unresponsive))
    if (state.launchNotice) RowNote(stringResource(R.string.music_open_manually))
    if (state.writeFailureNotice) RowNote(stringResource(R.string.session_write_failed))
}

@Composable
private fun StopAtEndSection(notice: StopAtEndNotice?, onStopNow: () -> Unit, onKeepListening: () -> Unit) {
    when (notice) {
        StopAtEndNotice.WAITING -> {
            RowText(stringResource(R.string.stop_at_end_waiting))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(stringResource(R.string.stop_at_end_now), onClick = onStopNow)
                PillButton(stringResource(R.string.stop_at_end_keep), onClick = onKeepListening)
            }
        }
        StopAtEndNotice.FAILED -> RowText(stringResource(R.string.stop_at_end_failed))
        null -> Unit
    }
}

/** FR-A016: "지금 몇 번째 곡인지" moves the song display only. Inline, like the result correction (SCR-07). */
@Composable
private fun CorrectSong(onCorrect: (Int) -> Boolean, onDone: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        RowText(stringResource(R.string.session_correct_title))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TaskField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(3) },
                placeholder = "",
                keyboardType = KeyboardType.Number,
                isError = error,
                modifier = Modifier.weight(1f).testTag("session_correct_input"),
            )
            PillButton(stringResource(R.string.session_correct_confirm), onClick = {
                val ok = input.toIntOrNull()?.let(onCorrect) == true
                error = !ok
                if (ok) onDone()
            })
        }
        if (error) RowNote(stringResource(R.string.session_correct_error))
    }
}
