package com.gb6105.musictimely.testing

import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.repository.TimerEffectSink

class FakeTimerEffectSink : TimerEffectSink {
    val received = mutableListOf<TimerEffect>()

    override suspend fun apply(session: TimerSession, effects: List<TimerEffect>) {
        received += effects
    }
}
