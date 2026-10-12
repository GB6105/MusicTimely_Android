package com.gb6105.musictimely.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.testing.FakeClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** 생각 메모 보관 (FR-A069, SRS v1 §9.3): 기기 내 보관, 결과에서 보관/삭제, 미선택 24시간 뒤 삭제. */
@RunWith(AndroidJUnit4::class)
class PreferencesMemoRepositoryIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = FakeClock(wallEpochMs = 1_000_000)
    private var nextId = 0
    private fun repository() = PreferencesMemoRepository(context, clock) { "m${nextId++}" }

    @Test
    fun `memos survive an app restart`() = runTest {
        repository().add("s", "참고 자료 링크 다시 찾기")

        assertThat(repository().memosFor("s").map { it.text }).containsExactly("참고 자료 링크 다시 찾기")
    }

    @Test
    fun `unhandled memos are gone after 24 hours, kept ones remain`() = runTest {
        val repo = repository()
        repo.add("s1", "그냥 둔 메모")
        repo.add("s2", "보관한 메모")
        repo.keep("s2")

        clock.advance(86_400_001)

        assertThat(repository().memosFor("s1")).isEmpty()
        assertThat(repository().memosFor("s2").map { it.text }).containsExactly("보관한 메모")
    }

    @Test
    fun `discarding removes a session's memos`() = runTest {
        val repo = repository()
        repo.add("s", "지울 메모")

        repo.discard("s")

        assertThat(repository().memosFor("s")).isEmpty()
    }
}
