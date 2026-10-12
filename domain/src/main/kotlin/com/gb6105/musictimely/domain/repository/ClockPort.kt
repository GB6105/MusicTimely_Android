package com.gb6105.musictimely.domain.repository

/** The only source of time for the domain (SRS Android §5.4). */
interface ClockPort {
    /** Monotonic time including deep sleep (`SystemClock.elapsedRealtime`). */
    fun monotonicNowMs(): Long

    fun wallNowEpochMs(): Long

    /** `Settings.Global.BOOT_COUNT`; a change means monotonic continuity is lost. */
    fun bootCount(): Int
}
