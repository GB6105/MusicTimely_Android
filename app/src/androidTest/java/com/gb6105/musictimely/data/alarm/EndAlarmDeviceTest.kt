package com.gb6105.musictimely.data.alarm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.appGraph
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.eventually
import com.gb6105.musictimely.shell
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real AlarmManager path (SRS Android §7.4): starting a session registers one inexact allow-while-idle alarm,
 * its delivery reaches the limit through SessionActionReceiver, and finishing cancels it. No exact-alarm permission.
 */
@RunWith(AndroidJUnit4::class)
class EndAlarmDeviceTest {
    private val sessions = appGraph.sessions()
    private val dispatch = appGraph.dispatch()

    /** Pending alarms only (`ELAPSED_WAKEUP #n: Alarm{… pkg}`); removal history lines do not count. */
    private val pending = Regex("""ELAPSED_WAKEUP #\d+: Alarm\{.* com\.gb6105\.musictimely\}""")

    private fun pendingEndAlarms() = shell("dumpsys alarm").lines().count { pending.containsMatchIn(it) }

    private fun start(targetMs: Long) = runBlocking {
        sessions.withLock { sessions.save(TimerSession.ready("alarm-device", Allocation.bounded(targetMs))) }
        dispatch(TimerCommand.Start("alarm-device-start"))
    }

    @After
    fun finish() = runBlocking {
        dispatch(TimerCommand.Finish("alarm-device-finish"))
        sessions.clear()
    }

    @Test
    fun startingRegistersOneWakeupEndAlarmAndFinishingCancelsIt() {
        start(targetMs = 30 * 60_000L)

        assertThat(eventually { pendingEndAlarms() == 1 }).isTrue()

        runBlocking { dispatch(TimerCommand.Finish("alarm-device-finish-early")) }

        assertThat(eventually { pendingEndAlarms() == 0 }).isTrue()
    }

    @Test
    fun theDeliveredAlarmMovesTheSessionToTheLimit() {
        start(targetMs = 3_000L)

        // Inexact by design (no SCHEDULE_EXACT_ALARM): allow the OS a generous delivery window.
        val reached = eventually(timeoutMs = 120_000) {
            runBlocking { sessions.active()?.state } == TimerState.LIMIT_REACHED
        }

        assertThat(reached).isTrue()
    }
}
