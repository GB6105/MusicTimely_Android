package com.gb6105.musictimely.ui.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** D0 elapsed amount U = E/d: one decimal place, whole numbers without a decimal (SRS v1 6.3). */
class FormatTest {
    @Test
    fun `tenths show one decimal place`() {
        assertThat(formatTenths(24)).isEqualTo("2.4")
        assertThat(formatTenths(5)).isEqualTo("0.5")
    }

    @Test
    fun `whole numbers omit the decimal`() {
        assertThat(formatTenths(70)).isEqualTo("7")
        assertThat(formatTenths(0)).isEqualTo("0")
    }

    @Test
    fun `clock is mm-ss without wrapping minutes`() {
        assertThat(formatClock(564_000)).isEqualTo("09:24")
        assertThat(formatClock(75 * 60_000L)).isEqualTo("75:00")
    }
}
