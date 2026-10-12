package com.gb6105.musictimely.data.alarm

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.receiver.SessionActionReceiver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** F08 종료 알람 (SRS Android §7.6, DEC-A06). FR-A064·A065. */
@RunWith(AndroidJUnit4::class)
class EndAlarmSchedulerIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val scheduler = EndAlarmScheduler(context)
    private val session = TimerSession.ready("s-1", Allocation.bounded(25 * 60_000L))

    @Test
    fun `schedules an inexact allow-while-idle alarm on the elapsed realtime clock`() = runTest {
        scheduler.handle(session, TimerEffect.ScheduleEndAlarm(atMonoMs = 1_500_000, targetRevision = 0))

        val alarm = alarms.scheduledAlarms.single()
        assertThat(alarm.type).isEqualTo(AlarmManager.ELAPSED_REALTIME_WAKEUP)
        assertThat(alarm.triggerAtMs).isEqualTo(1_500_000)
        assertThat(alarm.allowWhileIdle).isTrue()
        assertThat(alarm.operation!!.intentFor().component?.className).isEqualTo(SessionActionReceiver::class.java.name)
        assertThat(alarm.operation!!.intentFor().getStringExtra(SessionActionReceiver.EXTRA_KEY)).isEqualTo("s-1:0:end")
    }

    @Test
    fun `a later revision replaces the pending alarm (upsert by key)`() = runTest {
        scheduler.handle(session, TimerEffect.ScheduleEndAlarm(1_500_000, 0))
        scheduler.handle(session, TimerEffect.ScheduleEndAlarm(1_800_000, 1))

        val alarm = alarms.scheduledAlarms.single()
        assertThat(alarm.triggerAtMs).isEqualTo(1_800_000)
        assertThat(alarm.operation!!.intentFor().getStringExtra(SessionActionReceiver.EXTRA_KEY)).isEqualTo("s-1:1:end")
    }

    @Test
    fun `cancel removes the pending alarm`() = runTest {
        scheduler.handle(session, TimerEffect.ScheduleEndAlarm(1_500_000, 0))

        scheduler.handle(session, TimerEffect.CancelEndAlarm)

        assertThat(alarms.scheduledAlarms).isEmpty()
    }

    @Test
    fun `handles only alarm effects`() {
        assertThat(scheduler.handles(TimerEffect.CancelEndAlarm)).isTrue()
        assertThat(scheduler.handles(TimerEffect.ScheduleEndAlarm(1, 0))).isTrue()
        assertThat(scheduler.handles(TimerEffect.ShowProgress)).isFalse()
    }

    private fun android.app.PendingIntent.intentFor() = shadowOf(this).savedIntent
}
