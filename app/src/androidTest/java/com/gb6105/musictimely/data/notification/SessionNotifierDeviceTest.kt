package com.gb6105.musictimely.data.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerStateMachine
import com.gb6105.musictimely.shell
import com.gb6105.musictimely.testing.FakeClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The real NotificationManager: one silent ongoing progress notification, cleared at the end (SRS Android §7.5). */
@RunWith(AndroidJUnit4::class)
class SessionNotifierDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val clock = FakeClock(monotonicMs = 0, wallEpochMs = System.currentTimeMillis())
    private val notifier = SessionNotifier(context, clock, ProgressDismissal(), isAppInForeground = { true })
    private val running = TimerStateMachine().reduce(
        TimerSession.ready("device", Allocation.bounded(25 * 60_000L), taskTitle = "비밀 작업"), TimerCommand.Start("a"), 0,
    ).session

    @Before
    fun grant() {
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
    }

    @After
    fun clear() = runBlocking { notifier.handle(running, TimerEffect.ClearNotifications) }

    /** Posting is asynchronous in NotificationManagerService. */
    private fun progressPosted(): Boolean {
        repeat(50) {
            if (manager.activeNotifications.any { it.id == SessionNotifier.ID_PROGRESS }) return true
            Thread.sleep(100)
        }
        return false
    }

    private fun progressGone(): Boolean {
        repeat(50) {
            if (manager.activeNotifications.none { it.id == SessionNotifier.ID_PROGRESS }) return true
            Thread.sleep(100)
        }
        return false
    }

    @Test
    fun progressIsOneSilentOngoingNotificationWithoutTheTaskTitle() = runBlocking {
        notifier.handle(running, TimerEffect.ShowProgress)

        assertThat(progressPosted()).isTrue()
        val posted = manager.activeNotifications.single { it.id == SessionNotifier.ID_PROGRESS }.notification
        assertThat(posted.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(manager.getNotificationChannel(SessionNotifier.CHANNEL_PROGRESS).importance)
            .isEqualTo(NotificationManager.IMPORTANCE_LOW)
        assertThat(posted.extras.toString()).doesNotContain("비밀 작업")
    }

    @Test
    fun finishingClearsTheProgressNotification() = runBlocking {
        notifier.handle(running, TimerEffect.ShowProgress)
        assertThat(progressPosted()).isTrue()

        notifier.handle(running, TimerEffect.ClearNotifications)

        assertThat(progressGone()).isTrue()
    }
}
