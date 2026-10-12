package com.gb6105.musictimely.data.effect

import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CompositeTimerEffectSinkTest {

    private class Recording(private val accepts: Set<Class<out TimerEffect>>) : TimerEffectHandler {
        val handled = mutableListOf<TimerEffect>()
        override fun handles(effect: TimerEffect) = effect::class.java in accepts
        override suspend fun handle(session: TimerSession, effect: TimerEffect) {
            handled += effect
        }
    }

    private val session = TimerSession.ready("s", Allocation.bounded(60_000))

    @Test
    fun `each effect goes to every handler that handles it, in order`() = runTest {
        val alarms = Recording(setOf(TimerEffect.CancelEndAlarm::class.java, TimerEffect.ScheduleEndAlarm::class.java))
        val notifications = Recording(setOf(TimerEffect.ShowProgress::class.java))
        val sink = CompositeTimerEffectSink(setOf(alarms, notifications))

        sink.apply(session, listOf(TimerEffect.CancelEndAlarm, TimerEffect.ShowProgress, TimerEffect.ScheduleEndAlarm(5, 0)))

        assertThat(alarms.handled).containsExactly(TimerEffect.CancelEndAlarm, TimerEffect.ScheduleEndAlarm(5, 0)).inOrder()
        assertThat(notifications.handled).containsExactly(TimerEffect.ShowProgress)
    }

    @Test
    fun `a failing handler does not stop the others (MT-T7)`() = runTest {
        val failing = object : TimerEffectHandler {
            override fun handles(effect: TimerEffect) = true
            override suspend fun handle(session: TimerSession, effect: TimerEffect) = throw SecurityException("revoked")
        }
        val after = Recording(setOf(TimerEffect.ShowProgress::class.java))

        CompositeTimerEffectSink(linkedSetOf(failing, after)).apply(session, listOf(TimerEffect.ShowProgress))

        assertThat(after.handled).containsExactly(TimerEffect.ShowProgress)
    }
}
