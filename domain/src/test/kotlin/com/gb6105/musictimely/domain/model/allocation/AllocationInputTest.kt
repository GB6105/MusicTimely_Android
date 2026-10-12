package com.gb6105.musictimely.domain.model.allocation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** FR-A001·A002·A004, SRS v1 §6.4, QA-03·QA-41. */
class AllocationInputTest {

    @Test
    fun `presets are 10, 25 and 40 minutes with 25 selected first (FR-A002)`() {
        assertThat(AllocationInput.PRESET_MINUTES).containsExactly(10, 25, 40).inOrder()
        assertThat(AllocationInput.DEFAULT_MINUTES).isEqualTo(25)
    }

    @Test
    fun `custom minutes accept integers 1 to 240 (QA-03)`() {
        assertThat(AllocationInput.parseMinutes("1")).isEqualTo(MinutesInput.Valid(1))
        assertThat(AllocationInput.parseMinutes(" 240 ")).isEqualTo(MinutesInput.Valid(240))
        listOf("0", "-5", "2.5", "241", "abc", "").forEach {
            assertThat(AllocationInput.parseMinutes(it)).isEqualTo(MinutesInput.Invalid)
        }
    }

    @Test
    fun `blank task title becomes no title`() {
        assertThat(TaskTitle.from("   ")).isNull()
        assertThat(TaskTitle.from("  기획안 쓰기 ")?.value).isEqualTo("기획안 쓰기")
    }

    @Test
    fun `task title keeps at most 100 user-perceived characters`() {
        val family = "👨‍👩‍👧"
        val title = TaskTitle.from(family.repeat(120))

        assertThat(title?.value).isEqualTo(family.repeat(100))
    }

    @Test
    fun `average song length defaults to 240 seconds and accepts 60 to 1800 (FR-A004)`() {
        assertThat(SongLength.DEFAULT_MS).isEqualTo(240_000)
        assertThat(SongLength.isValidSeconds(60)).isTrue()
        assertThat(SongLength.isValidSeconds(1_800)).isTrue()
        assertThat(SongLength.isValidSeconds(59)).isFalse()
        assertThat(SongLength.isValidSeconds(1_801)).isFalse()
    }

    @Test
    fun `loop length parses m colon ss within 60 to 1800 seconds (QA-41)`() {
        assertThat(SongLength.parseLoop("03:42")).isEqualTo(LoopInput.Valid(222_000))
        assertThat(SongLength.parseLoop("")).isEqualTo(LoopInput.Blank)
        assertThat(SongLength.parseLoop("0:59")).isEqualTo(LoopInput.Invalid)
        assertThat(SongLength.parseLoop("30:01")).isEqualTo(LoopInput.Invalid)
        assertThat(SongLength.parseLoop("3:7")).isEqualTo(LoopInput.Invalid)
        assertThat(SongLength.parseLoop("abc")).isEqualTo(LoopInput.Invalid)
    }
}
