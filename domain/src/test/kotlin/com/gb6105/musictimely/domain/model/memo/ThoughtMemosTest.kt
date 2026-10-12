package com.gb6105.musictimely.domain.model.memo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 생각 메모 (FR-A069, SRS v1 FR-037): 메모당 500자, 활성 세션 20개, 로컬 보관. QA-28. */
class ThoughtMemosTest {

    @Test
    fun `adds a trimmed memo`() {
        val result = ThoughtMemos.empty("s").add(id = "m1", text = "  참고 자료 링크 다시 찾기 ", atEpochMs = 5)

        result as MemoAdd.Added
        assertThat(result.memos.items).containsExactly(ThoughtMemo("m1", "참고 자료 링크 다시 찾기", 5))
    }

    @Test
    fun `blank, over 500 characters or a 21st memo is refused and nothing changes`() {
        val base = ThoughtMemos.empty("s")
        assertThat(base.add("m", "   ", 0)).isEqualTo(MemoAdd.Blank)
        assertThat(base.add("m", "가".repeat(501), 0)).isEqualTo(MemoAdd.TooLong)
        assertThat((base.add("m", "가".repeat(500), 0) as MemoAdd.Added).memos.items).hasSize(1)

        var full = base
        repeat(20) { full = (full.add("m$it", "메모 $it", it.toLong()) as MemoAdd.Added).memos }
        assertThat(full.add("m21", "하나 더", 21)).isEqualTo(MemoAdd.Full)
    }

    @Test
    fun `unhandled memos expire after 24 hours, kept memos stay (SRS v1 9_3)`() {
        val day = 86_400_000L
        val fresh = ThoughtMemo("a", "새것", createdAtEpochMs = 10 * day)
        val old = ThoughtMemo("b", "오래됨", createdAtEpochMs = 8 * day)
        val kept = ThoughtMemo("c", "보관", createdAtEpochMs = 1 * day, kept = true)

        assertThat(MemoRetention.alive(listOf(fresh, old, kept), nowEpochMs = 10 * day + 1)).containsExactly(fresh, kept)
    }
}
