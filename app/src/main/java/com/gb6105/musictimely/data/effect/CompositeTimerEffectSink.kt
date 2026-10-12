package com.gb6105.musictimely.data.effect

import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.repository.TimerEffectSink
import kotlinx.coroutines.CancellationException

/** One platform concern (alarm, notification, observation …) that performs some timer effects. */
interface TimerEffectHandler {
    fun handles(effect: TimerEffect): Boolean
    suspend fun handle(session: TimerSession, effect: TimerEffect)
}

/**
 * Routes each effect to the handlers that perform it. A handler failure (e.g. SecurityException after a
 * permission is revoked) is contained so the music layer can never stop the timer (MT-T7).
 * Exception messages are not logged: they may carry song or package data (SRS Android §8).
 */
class CompositeTimerEffectSink(private val handlers: Set<TimerEffectHandler>) : TimerEffectSink {
    override suspend fun apply(session: TimerSession, effects: List<TimerEffect>) {
        effects.forEach { effect ->
            handlers.filter { it.handles(effect) }.forEach { handler ->
                try {
                    handler.handle(session, effect)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Contained by design; the timer state is already persisted.
                }
            }
        }
    }
}
