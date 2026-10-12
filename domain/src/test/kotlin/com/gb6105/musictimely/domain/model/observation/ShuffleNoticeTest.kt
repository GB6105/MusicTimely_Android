package com.gb6105.musictimely.domain.model.observation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** FR-A022 (SRS v1 FR-022): shuffle notice only with evidence, at most once per session; never turns shuffle off. */
class ShuffleNoticeTest {

    @Test
    fun `unknown shuffle never shows a notice (D0)`() {
        val notice = ShuffleNotice()

        assertThat(notice.on(shuffle = null).show).isFalse()
    }

    @Test
    fun `shuffle off shows nothing`() {
        assertThat(ShuffleNotice().on(shuffle = false).show).isFalse()
    }

    @Test
    fun `known shuffle shows the notice once per session`() {
        val first = ShuffleNotice().on(shuffle = true)
        assertThat(first.show).isTrue()

        val again = first.next.on(shuffle = true)
        assertThat(again.show).isFalse()
        assertThat(again.next.on(shuffle = null).next.on(shuffle = true).show).isFalse()
    }
}
