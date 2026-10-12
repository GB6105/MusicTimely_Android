package com.gb6105.musictimely.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gb6105.musictimely.R
import com.gb6105.musictimely.ui.allocation.AllocationRoute
import com.gb6105.musictimely.ui.allocation.AllocationViewModel
import com.gb6105.musictimely.ui.component.formatClock
import com.gb6105.musictimely.ui.result.ResultScreen
import com.gb6105.musictimely.ui.result.ResultUiState
import com.gb6105.musictimely.ui.session.SessionRoute
import com.gb6105.musictimely.ui.memo.MemoRoute
import com.gb6105.musictimely.ui.memo.MemoViewModel
import com.gb6105.musictimely.ui.music.MusicSheetRoute
import com.gb6105.musictimely.ui.music.MusicSheetViewModel
import com.gb6105.musictimely.ui.seen.SeenRoute
import com.gb6105.musictimely.ui.seen.SeenViewModel
import com.gb6105.musictimely.ui.session.SessionViewModel
import com.gb6105.musictimely.ui.settings.SettingsRoute
import com.gb6105.musictimely.ui.settings.SettingsViewModel

@Composable
fun MusicTimelyApp(
    appViewModel: AppViewModel,
    allocationViewModel: @Composable () -> AllocationViewModel,
    sessionViewModel: @Composable () -> SessionViewModel,
    settingsViewModel: @Composable () -> SettingsViewModel,
    musicSheetViewModel: @Composable () -> MusicSheetViewModel,
    seenViewModel: @Composable () -> SeenViewModel,
    memoViewModel: @Composable () -> MemoViewModel,
    modifier: Modifier = Modifier,
    tickIntervalMs: Long = 1_000,
) {
    // Edge-to-edge: the background fills behind the system bars, content stays inside the safe area.
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AppContent(appViewModel, allocationViewModel, sessionViewModel, settingsViewModel, musicSheetViewModel, seenViewModel, memoViewModel, tickIntervalMs)
    }
}

@Composable
private fun AppContent(
    appViewModel: AppViewModel,
    allocationViewModel: @Composable () -> AllocationViewModel,
    sessionViewModel: @Composable () -> SessionViewModel,
    settingsViewModel: @Composable () -> SettingsViewModel,
    musicSheetViewModel: @Composable () -> MusicSheetViewModel,
    seenViewModel: @Composable () -> SeenViewModel,
    memoViewModel: @Composable () -> MemoViewModel,
    tickIntervalMs: Long,
) {
    val route by appViewModel.route.collectAsStateWithLifecycle()
    val overlays by appViewModel.overlays.collectAsStateWithLifecycle()
    // Back on screen: re-post progress (first session after the permission dialog, DEC-A15) and resume observation.
    LifecycleResumeEffect(appViewModel) {
        appViewModel.onResume()
        onPauseOrDispose {}
    }
    when (val top = overlays.lastOrNull()) {
        is Overlay.Settings -> {
            SettingsRoute(
                settingsViewModel(),
                onBack = appViewModel::closeOverlay,
                onSeen = { appViewModel.open(Overlay.Seen) },
                enableCountingOnOpen = top.enableCounting,
                onDataDeleted = appViewModel::onDataDeleted,
            )
            return
        }
        Overlay.MusicSheet -> {
            MusicSheetRoute(musicSheetViewModel(), onClose = appViewModel::closeOverlay)
            return
        }
        Overlay.Seen -> {
            SeenRoute(seenViewModel(), onBack = appViewModel::closeOverlay)
            return
        }
        is Overlay.Memo -> {
            MemoRoute(memoViewModel(), sessionId = top.sessionId, onClose = appViewModel::closeOverlay)
            return
        }
        null -> Unit
    }
    when (val current = route) {
        AppRoute.Loading -> Unit
        AppRoute.Allocation -> {
            val viewModel = allocationViewModel()
            AllocationRoute(
                viewModel,
                onStarted = appViewModel::onStarted,
                onOpenSettings = { appViewModel.open(Overlay.Settings()) },
                onOpenMusic = { appViewModel.open(Overlay.MusicSheet) },
            )
        }
        AppRoute.Session -> SessionRoute(
            sessionViewModel(),
            onFinished = appViewModel::onFinished,
            tickIntervalMs = tickIntervalMs,
            onEnableCounting = { appViewModel.open(Overlay.Settings(enableCounting = true)) },
            onOpenMusic = { appViewModel.open(Overlay.MusicSheet) },
            onOpenMemo = appViewModel::openMemo,
            onOpenSettings = { appViewModel.open(Overlay.Settings()) },
        )
        is AppRoute.Result -> ResultScreen(
            ResultUiState.from(current.session, current.observed, current.playlist).copy(memos = current.memos),
            onRestart = appViewModel::onRestart,
            onKeepMemos = { appViewModel.keepMemos(current.session.sessionId) },
            onDiscardMemos = { appViewModel.discardMemos(current.session.sessionId) },
        )
        is AppRoute.Recovery -> ChoiceScreen(
            title = stringResource(R.string.recovery_title),
            body = stringResource(R.string.recovery_body, formatClock(current.session.accumulatedActiveMs)),
            primary = stringResource(R.string.recovery_resume) to appViewModel::resumeFromRecovery,
            secondary = stringResource(R.string.recovery_finish) to appViewModel::finishFromRecovery,
        )
        AppRoute.Corrupt -> ChoiceScreen(
            title = stringResource(R.string.corrupt_title),
            body = null,
            primary = stringResource(R.string.corrupt_discard) to appViewModel::discardCorrupt,
            secondary = null,
        )
    }
}

@Composable
private fun ChoiceScreen(
    title: String,
    body: String?,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Button(onClick = primary.second, modifier = Modifier.fillMaxWidth()) { Text(primary.first) }
        secondary?.let { (label, action) ->
            OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
    }
}
