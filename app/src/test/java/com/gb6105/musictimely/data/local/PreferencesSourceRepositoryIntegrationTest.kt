package com.gb6105.musictimely.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.source.Provider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** 저장한 음악 소스 (SRS Android §9.2): 기기 설정에만, 삭제 가능 (MT-S5, FR-A071). */
@RunWith(AndroidJUnit4::class)
class PreferencesSourceRepositoryIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val link = ExternalSource("a", Provider.YOUTUBE_MUSIC, "com.google.android.apps.youtube.music", "https://music.youtube.com/playlist?list=PL1", "유튜브뮤직 플레이리스트", 5)
    private val appOnly = ExternalSource("b", Provider.MELON, "com.iloen.melon", null, "멜론", 6)

    @Test
    fun `saved sources survive a restart in order`() = runTest {
        PreferencesSourceRepository(context).apply {
            save(link)
            save(appOnly)
        }

        assertThat(PreferencesSourceRepository(context).sources.value).containsExactly(link, appOnly).inOrder()
    }

    @Test
    fun `saving the same id replaces it (rename)`() = runTest {
        val repository = PreferencesSourceRepository(context)
        repository.save(link)

        repository.save(link.copy(label = "작업용"))

        assertThat(repository.sources.value.single().label).isEqualTo("작업용")
    }

    @Test
    fun `delete and clear remove sources`() = runTest {
        val repository = PreferencesSourceRepository(context)
        repository.save(link)
        repository.save(appOnly)

        repository.delete("a")
        assertThat(repository.sources.value).containsExactly(appOnly)

        repository.clear()
        assertThat(PreferencesSourceRepository(context).sources.value).isEmpty()
    }
}
