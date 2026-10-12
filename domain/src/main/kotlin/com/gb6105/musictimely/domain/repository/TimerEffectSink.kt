package com.gb6105.musictimely.domain.repository

import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.model.timer.TimerSession

/** Performs timer side effects (alarm, notification, observation) in the data layer. */
interface TimerEffectSink {
    suspend fun apply(session: TimerSession, effects: List<TimerEffect>)
}
