package com.gb6105.musictimely.domain.model.allocation

import java.text.BreakIterator

sealed interface MinutesInput {
    data class Valid(val minutes: Int) : MinutesInput
    data object Invalid : MinutesInput
}

/** FR-A002 (DEC-10): presets and custom integer minutes 1..240. No duration-prediction question. */
object AllocationInput {
    val PRESET_MINUTES: List<Int> = listOf(10, 25, 40)
    const val DEFAULT_MINUTES: Int = 25
    private val RANGE = 1..240

    fun parseMinutes(text: String): MinutesInput {
        val minutes = text.trim().takeIf { it.all(Char::isDigit) }?.toIntOrNull() ?: return MinutesInput.Invalid
        return if (minutes in RANGE) MinutesInput.Valid(minutes) else MinutesInput.Invalid
    }
}

/** FR-A001: optional; blank means no title, at most 100 user-perceived characters. */
@JvmInline
value class TaskTitle(val value: String) {
    companion object {
        const val MAX_GRAPHEMES = 100

        fun from(raw: String): TaskTitle? {
            val trimmed = raw.trim().takeIf { it.isNotEmpty() } ?: return null
            return TaskTitle(trimmed.takeGraphemes(MAX_GRAPHEMES))
        }

        private fun String.takeGraphemes(max: Int): String {
            val boundaries = BreakIterator.getCharacterInstance().also { it.setText(this) }
            var end = 0
            repeat(max) {
                val next = boundaries.next()
                if (next == BreakIterator.DONE) return this
                end = next
            }
            return substring(0, end)
        }
    }
}

sealed interface LoopInput {
    data class Valid(val durationMs: Long) : LoopInput
    data object Blank : LoopInput
    data object Invalid : LoopInput
}

/** FR-A004 average song length and SRS v1 §6.4 single-loop length, both 60..1800 seconds. */
object SongLength {
    const val DEFAULT_MS: Long = 240_000
    private val SECONDS_RANGE = 60..1_800
    private val LOOP_PATTERN = Regex("""^(\d{1,2}):([0-5]\d)$""")

    fun isValidSeconds(seconds: Int): Boolean = seconds in SECONDS_RANGE

    fun parseLoop(text: String): LoopInput {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return LoopInput.Blank
        val match = LOOP_PATTERN.matchEntire(trimmed) ?: return LoopInput.Invalid
        val seconds = match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
        return if (isValidSeconds(seconds)) LoopInput.Valid(seconds * 1_000L) else LoopInput.Invalid
    }
}
