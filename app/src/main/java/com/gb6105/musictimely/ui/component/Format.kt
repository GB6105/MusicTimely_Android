package com.gb6105.musictimely.ui.component

/** mm:ss from milliseconds (minutes are not wrapped at 60). */
fun formatClock(ms: Long): String {
    val totalSeconds = (ms / 1_000).coerceAtLeast(0)
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** Tenths → "2.4", whole numbers without a decimal → "7". */
fun formatTenths(tenths: Int): String = if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
