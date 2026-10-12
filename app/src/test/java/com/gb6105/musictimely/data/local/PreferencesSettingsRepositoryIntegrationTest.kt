package com.gb6105.musictimely.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.audio.NoiseKind
import com.gb6105.musictimely.domain.model.audio.NoiseSound
import com.gb6105.musictimely.domain.model.settings.MusicSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** 설정 저장 (FR-A070, SRS v1 FR-038): 재시작 후 유지, 기본값 복구, 삭제. */
@RunWith(AndroidJUnit4::class)
class PreferencesSettingsRepositoryIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `starts with defaults`() {
        assertThat(PreferencesSettingsRepository(context).settings.value).isEqualTo(MusicSettings())
    }

    @Test
    fun `updates survive an app restart`() = runTest {
        val changed = MusicSettings(
            selectedSourceId = "src-1", durationPerUnitMs = 200_000, songCountingEnabled = true, stopAtEndEnabled = true,
            permissionPromptShown = true, ytmNoticeShown = true, showSongs = false, notificationPromptShown = true,
            selectedNoise = NoiseKind.PINK, noMusic = true, singleLoop = true, loopDurationMs = 222_000,
            sound = NoiseSound(volume = 0.55f, fadeInMs = 0, fadeOutMs = 10_000),
            pauseTimerWithMusic = true,
        )
        PreferencesSettingsRepository(context).update { changed }

        assertThat(PreferencesSettingsRepository(context).settings.value).isEqualTo(changed)
    }

    @Test
    fun `the flow emits the updated value`() = runTest {
        val repository = PreferencesSettingsRepository(context)

        repository.update { it.copy(songCountingEnabled = true) }

        assertThat(repository.settings.value.songCountingEnabled).isTrue()
    }

    @Test
    fun `clear restores defaults`() = runTest {
        val repository = PreferencesSettingsRepository(context)
        repository.update { it.copy(songCountingEnabled = true, selectedSourceId = "x") }

        repository.clear()

        assertThat(repository.settings.value).isEqualTo(MusicSettings())
        assertThat(PreferencesSettingsRepository(context).settings.value).isEqualTo(MusicSettings())
    }

    @Test
    fun `out-of-range stored sound and loop values fall back to defaults (review L2)`() = runTest {
        PreferencesSettingsRepository(context).update { it.copy(singleLoop = true) }
        context.getSharedPreferences("music_settings", Context.MODE_PRIVATE).edit()
            .putFloat("noise_volume", 5f)
            .putLong("noise_fade_in_ms", 99_999)
            .putLong("noise_fade_out_ms", -1)
            .putLong("loop_duration_ms", 10)
            .commit()

        val read = PreferencesSettingsRepository(context).settings.value

        assertThat(read.sound).isEqualTo(NoiseSound())
        assertThat(read.loopDurationMs).isNull()
        assertThat(read.singleLoop).isTrue()
    }

    @Test
    fun `values written under another version are not trusted and read as defaults (FR-A070 versioned keys)`() = runTest {
        PreferencesSettingsRepository(context).update { it.copy(showSongs = false, sound = NoiseSound(volume = 0.7f)) }
        context.getSharedPreferences("music_settings", Context.MODE_PRIVATE).edit().putInt("version", 99).commit()

        assertThat(PreferencesSettingsRepository(context).settings.value).isEqualTo(MusicSettings())
    }
}
