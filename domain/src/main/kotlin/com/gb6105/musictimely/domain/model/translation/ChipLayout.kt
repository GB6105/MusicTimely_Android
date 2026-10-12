package com.gb6105.musictimely.domain.model.translation

/** FR-A021: at most eight visual chips; larger allocations show the remaining summary instead. */
sealed interface ChipLayout {
    data class Chips(val total: Int, val filled: Int) : ChipLayout
    data class Summary(val total: Int, val filled: Int) : ChipLayout

    companion object {
        const val MAX_VISUAL_CHIPS = 8

        fun of(totalSongs: Int, filledChips: Int): ChipLayout =
            if (totalSongs <= MAX_VISUAL_CHIPS) Chips(totalSongs, filledChips) else Summary(totalSongs, filledChips)
    }
}
