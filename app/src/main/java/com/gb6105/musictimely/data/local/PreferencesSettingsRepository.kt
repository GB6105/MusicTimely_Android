package com.gb6105.musictimely.data.local

import com.gb6105.musictimely.domain.model.allocation.SongLength
import com.gb6105.musictimely.domain.model.audio.NoiseSound
import android.content.Context
import android.content.SharedPreferences
import com.gb6105.musictimely.domain.model.audio.NoiseKind
import com.gb6105.musictimely.domain.model.settings.MusicSettings
import com.gb6105.musictimely.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** FR-A070: versioned key-value settings in app-private preferences, kept until the user deletes data. */
class PreferencesSettingsRepository(context: Context) : SettingsRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    override val settings: StateFlow<MusicSettings> = state

    override suspend fun update(transform: (MusicSettings) -> MusicSettings) {
        val next = transform(state.value)
        withContext(Dispatchers.IO) { write(next) }
        state.value = next
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { prefs.edit().clear().commit() }
        state.value = MusicSettings()
    }

    private fun read(): MusicSettings {
        val defaults = MusicSettings()
        if (prefs.getInt(KEY_VERSION, 0) != VERSION) return defaults
        return MusicSettings(
            selectedSourceId = prefs.getString(KEY_SOURCE, null),
            durationPerUnitMs = prefs.getLong(KEY_UNIT, defaults.durationPerUnitMs),
            songCountingEnabled = prefs.getBoolean(KEY_COUNTING, defaults.songCountingEnabled),
            stopAtEndEnabled = prefs.getBoolean(KEY_STOP_AT_END, defaults.stopAtEndEnabled),
            permissionPromptShown = prefs.getBoolean(KEY_PROMPT_SHOWN, defaults.permissionPromptShown),
            ytmNoticeShown = prefs.getBoolean(KEY_YTM_NOTICE, defaults.ytmNoticeShown),
            showSongs = prefs.getBoolean(KEY_SHOW_SONGS, defaults.showSongs),
            notificationPromptShown = prefs.getBoolean(KEY_NOTIFICATION_PROMPT, defaults.notificationPromptShown),
            selectedNoise = prefs.getString(KEY_NOISE, null)?.let { name -> NoiseKind.entries.firstOrNull { it.name == name } },
            noMusic = prefs.getBoolean(KEY_NO_MUSIC, defaults.noMusic),
            singleLoop = prefs.getBoolean(KEY_SINGLE_LOOP, defaults.singleLoop),
            pauseTimerWithMusic = prefs.getBoolean(KEY_PAUSE_WITH_MUSIC, defaults.pauseTimerWithMusic),
            // Stored values outside the allowed ranges fall back to defaults (review L2).
            loopDurationMs = prefs.getLong(KEY_LOOP_MS, 0)
                .takeIf { it % 1_000 == 0L && SongLength.isValidSeconds((it / 1_000).toInt()) },
            sound = NoiseSound(
                volume = prefs.getFloat(KEY_VOLUME, defaults.sound.volume).takeIf(NoiseSound::isValidVolume) ?: defaults.sound.volume,
                fadeInMs = prefs.getLong(KEY_FADE_IN, defaults.sound.fadeInMs).takeIf(NoiseSound::isValidFadeIn) ?: defaults.sound.fadeInMs,
                fadeOutMs = prefs.getLong(KEY_FADE_OUT, defaults.sound.fadeOutMs).takeIf(NoiseSound::isValidFadeOut) ?: defaults.sound.fadeOutMs,
            ),
        )
    }

    private fun write(s: MusicSettings) {
        prefs.edit()
            .putInt(KEY_VERSION, VERSION)
            .putString(KEY_SOURCE, s.selectedSourceId)
            .putLong(KEY_UNIT, s.durationPerUnitMs)
            .putBoolean(KEY_COUNTING, s.songCountingEnabled)
            .putBoolean(KEY_STOP_AT_END, s.stopAtEndEnabled)
            .putBoolean(KEY_PROMPT_SHOWN, s.permissionPromptShown)
            .putBoolean(KEY_YTM_NOTICE, s.ytmNoticeShown)
            .putBoolean(KEY_SHOW_SONGS, s.showSongs)
            .putBoolean(KEY_NOTIFICATION_PROMPT, s.notificationPromptShown)
            .putString(KEY_NOISE, s.selectedNoise?.name)
            .putBoolean(KEY_NO_MUSIC, s.noMusic)
            .putBoolean(KEY_SINGLE_LOOP, s.singleLoop)
            .putBoolean(KEY_PAUSE_WITH_MUSIC, s.pauseTimerWithMusic)
            .putLong(KEY_LOOP_MS, s.loopDurationMs ?: 0)
            .putFloat(KEY_VOLUME, s.sound.volume)
            .putLong(KEY_FADE_IN, s.sound.fadeInMs)
            .putLong(KEY_FADE_OUT, s.sound.fadeOutMs)
            .commit()
    }

    private companion object {
        const val FILE = "music_settings"
        const val VERSION = 1
        const val KEY_VERSION = "version"
        const val KEY_SOURCE = "selected_source_id"
        const val KEY_UNIT = "duration_per_unit_ms"
        const val KEY_COUNTING = "song_counting_enabled"
        const val KEY_STOP_AT_END = "stop_at_end_enabled"
        const val KEY_PROMPT_SHOWN = "permission_prompt_shown"
        const val KEY_YTM_NOTICE = "ytm_notice_shown"
        const val KEY_SHOW_SONGS = "show_songs"
        const val KEY_NOTIFICATION_PROMPT = "notification_prompt_shown"
        const val KEY_NOISE = "selected_noise"
        const val KEY_NO_MUSIC = "no_music"
        const val KEY_SINGLE_LOOP = "single_loop"
        const val KEY_PAUSE_WITH_MUSIC = "pause_timer_with_music"
        const val KEY_LOOP_MS = "loop_duration_ms"
        const val KEY_VOLUME = "noise_volume"
        const val KEY_FADE_IN = "noise_fade_in_ms"
        const val KEY_FADE_OUT = "noise_fade_out_ms"
    }
}
