package com.gb6105.musictimely.domain.model.result

/** F18 M3 "총 걸린 시간": the session's active time (pauses excluded), whole seconds rounded down. */
data class TotalTime(val hours: Int, val minutes: Int, val seconds: Int) {
    enum class Shape { SECONDS, MINUTES_SECONDS, HOURS_MINUTES_SECONDS }

    val shape: Shape
        get() = when {
            hours > 0 -> Shape.HOURS_MINUTES_SECONDS
            minutes > 0 -> Shape.MINUTES_SECONDS
            else -> Shape.SECONDS
        }

    companion object {
        fun of(activeMs: Long): TotalTime {
            val total = activeMs.coerceAtLeast(0) / 1_000
            return TotalTime((total / 3_600).toInt(), (total % 3_600 / 60).toInt(), (total % 60).toInt())
        }
    }
}
