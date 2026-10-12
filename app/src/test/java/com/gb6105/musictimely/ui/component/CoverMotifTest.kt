package com.gb6105.musictimely.ui.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Figma "MT / Playlist / A Result": app-drawn cover motifs (not album art) cycle sleeve → stripes → record. */
class CoverMotifTest {

    @Test
    fun `rows cycle sleeve, stripes and record by their index`() {
        assertThat((0..6).map(CoverMotif::variantFor)).containsExactly(
            CoverMotif.SLEEVE, CoverMotif.STRIPES, CoverMotif.RECORD,
            CoverMotif.SLEEVE, CoverMotif.STRIPES, CoverMotif.RECORD,
            CoverMotif.SLEEVE,
        ).inOrder()
    }

    @Test
    fun `a negative index still maps to a variant`() {
        assertThat(CoverMotif.variantFor(-1)).isEqualTo(CoverMotif.RECORD)
    }

    @Test
    fun `the collage always has four tiles, filled by cycling even with fewer tracks`() {
        assertThat(CoverMotif.collage()).containsExactly(
            CoverMotif.SLEEVE, CoverMotif.STRIPES, CoverMotif.RECORD, CoverMotif.SLEEVE,
        ).inOrder()
    }
}
