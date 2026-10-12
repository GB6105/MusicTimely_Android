package com.gb6105.musictimely.testing

import com.gb6105.musictimely.domain.repository.ClockPort

class FakeClock(
    var monotonicMs: Long = 0,
    var wallEpochMs: Long = 1_760_000_000_000,
    var boot: Int = 1,
) : ClockPort {
    override fun monotonicNowMs() = monotonicMs
    override fun wallNowEpochMs() = wallEpochMs
    override fun bootCount() = boot

    fun advance(ms: Long) {
        monotonicMs += ms
        wallEpochMs += ms
    }
}
