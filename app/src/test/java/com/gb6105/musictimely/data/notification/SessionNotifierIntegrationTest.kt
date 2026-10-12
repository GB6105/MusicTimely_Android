package com.gb6105.musictimely.data.notification

import android.Manifest
import android.app.Application
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
import com.gb6105.musictimely.testing.FakeClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** F08 진행·종료 알림 (SRS Android §7.6, SCR-14). FR-A061·A062·A064, QA-A29·A31. */
@RunWith(AndroidJUnit4::class)
class SessionNotifierIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val clock = FakeClock(monotonicMs = 0, wallEpochMs = 1_760_000_000_000)
    private var foreground = false
    private val dismissal = ProgressDismissal()
    private val slot = ProgressNotificationSlot()
    private var showSongs = true
    private val notifier = SessionNotifier(context, clock, dismissal, { foreground }, slot) { showSongs }
    private val machine = TimerStateMachine()
    private val minute = 60_000L

    private val running = machine.reduce(
        TimerSession.ready("s", Allocation.bounded(25 * minute), taskTitle = "비밀 작업"), TimerCommand.Start("a"), 0,
    ).session

    @Before
    fun grant() = shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    private fun progress(): Notification = shadowOf(manager).allNotifications.single { it.channelId == SessionNotifier.CHANNEL_PROGRESS }

    @Test
    fun `channels are a silent low progress channel and a default end channel without sound`() = runTest {
        notifier.handle(running, TimerEffect.ShowProgress)

        val progress = manager.getNotificationChannel(SessionNotifier.CHANNEL_PROGRESS)
        val end = manager.getNotificationChannel(SessionNotifier.CHANNEL_END)
        assertThat(progress.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
        assertThat(progress.sound).isNull()
        assertThat(end.importance).isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
        assertThat(end.sound).isNull()
        assertThat(end.shouldVibrate()).isTrue()
    }

    @Test
    fun `running progress counts down to the target with songs left and two timer actions (QA-A29)`() = runTest {
        clock.advance(564_000)

        notifier.handle(running, TimerEffect.ShowProgress)

        val n = progress()
        assertThat(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)).isTrue()
        assertThat(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)).isTrue()
        assertThat(n.`when`).isEqualTo(clock.wallEpochMs + 25 * minute - 564_000)
        assertThat(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString()).isEqualTo("약 4곡 분량 남음")
        assertThat(n.actions.map { it.title.toString() }).containsExactly("일시정지", "마침").inOrder()
        assertThat(n.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(n.flags and Notification.FLAG_ONLY_ALERT_ONCE).isNotEqualTo(0)
    }

    @Test
    fun `progress never shows the task title, a song or a music button (FR-A061, FR-A062)`() = runTest {
        notifier.handle(running, TimerEffect.ShowProgress)

        val n = progress()
        val texts = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT)
            .mapNotNull { n.extras.getCharSequence(it)?.toString() }
        assertThat(texts.joinToString()).doesNotContain("비밀 작업")
        assertThat(n.extras.getString(Notification.EXTRA_TEMPLATE)).isNull()
        assertThat(n.actions.map { it.title.toString() }).containsNoneOf("재생", "음악 일시정지", "음악 재생")
    }

    @Test
    fun `paused progress offers resume and finish without a countdown`() = runTest {
        val paused = machine.reduce(running, TimerCommand.Pause("p"), minute).session

        notifier.handle(paused, TimerEffect.ShowProgress)

        val n = progress()
        assertThat(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)).isFalse()
        assertThat(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString()).isEqualTo("타이머만 쉬고 있어요")
        assertThat(n.actions.map { it.title.toString() }).containsExactly("이어서", "마침").inOrder()
    }

    @Test
    fun `limit reached in the background posts the end notification (FR-A064)`() = runTest {
        val limit = machine.reduce(running, TimerCommand.ReachLimit("r"), 25 * minute).session

        notifier.handle(limit, TimerEffect.ShowLimitReached)

        val end = shadowOf(manager).allNotifications.single { it.channelId == SessionNotifier.CHANNEL_END }
        assertThat(end.extras.getCharSequence(Notification.EXTRA_TEXT).toString()).isEqualTo("정한 시간이 됐어요")
    }

    @Test
    fun `limit reached while the app is on screen shows only the in-app signal`() = runTest {
        foreground = true
        val limit = machine.reduce(running, TimerCommand.ReachLimit("r"), 25 * minute).session

        notifier.handle(limit, TimerEffect.ShowLimitReached)

        assertThat(shadowOf(manager).allNotifications.filter { it.channelId == SessionNotifier.CHANNEL_END }).isEmpty()
    }

    @Test
    fun `clear removes both notifications`() = runTest {
        notifier.handle(running, TimerEffect.ShowProgress)
        notifier.handle(running, TimerEffect.ShowLimitReached)

        notifier.handle(running, TimerEffect.ClearNotifications)

        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }

    @Test
    fun `without notification permission nothing is posted and nothing throws (QA-A31)`() = runTest {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifier.handle(running, TimerEffect.ShowProgress)

        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }

    @Test
    fun `the progress notification reports its dismissal to the app (DEC-A15)`() = runTest {
        notifier.handle(running, TimerEffect.ShowProgress)

        val deleteIntent = shadowOf(progress().deleteIntent).savedIntent
        assertThat(deleteIntent.action).isEqualTo(com.gb6105.musictimely.receiver.SessionActionReceiver.ACTION_PROGRESS_DISMISSED)
    }

    @Test
    fun `a dismissed progress notification is not re-posted from the background, only when the app is open (DEC-A15, QA-A30)`() = runTest {
        notifier.handle(running, TimerEffect.ShowProgress)
        manager.cancelAll()
        dismissal.markDismissed()

        notifier.handle(running, TimerEffect.ShowProgress)
        assertThat(shadowOf(manager).allNotifications).isEmpty()

        foreground = true
        notifier.handle(running, TimerEffect.ShowProgress)
        assertThat(shadowOf(manager).allNotifications.filter { it.channelId == SessionNotifier.CHANNEL_PROGRESS }).hasSize(1)
        assertThat(dismissal.isDismissed).isFalse()
    }

    @Test
    fun `the posted progress notification is shared for the sound service and cleared at the end (SRS Android 7_6)`() = runTest {
        notifier.handle(running, TimerEffect.ShowProgress)
        assertThat(slot.latest?.channelId).isEqualTo(SessionNotifier.CHANNEL_PROGRESS)

        notifier.handle(running, TimerEffect.ClearNotifications)
        assertThat(slot.latest).isNull()
    }

    @Test
    fun `a dismissed progress notification is not kept for the sound service to bring back (review M2)`() = runTest {
        notifier.handle(running, TimerEffect.ShowProgress)
        dismissal.markDismissed()
        foreground = false

        notifier.handle(running, TimerEffect.ShowProgress)

        assertThat(slot.latest).isNull()
    }

    @Test
    fun `a single-loop session counts loops in the notification too (FR-A019, review M4)`() = runTest {
        val loop = machine.reduce(
            TimerSession.ready("l", Allocation.bounded(25 * minute, loopDurationMs = 222_000, singleLoop = true)), TimerCommand.Start("a"), 0,
        ).session
        clock.advance(444_000)

        notifier.handle(loop, TimerEffect.ShowProgress)

        assertThat(progress().extras.getCharSequence(Notification.EXTRA_TEXT).toString()).isEqualTo("약 5바퀴 분량 남음")
    }

    @Test
    fun `song display off keeps the countdown without song amounts (FR-A020, review M4)`() = runTest {
        showSongs = false
        clock.advance(564_000)

        notifier.handle(running, TimerEffect.ShowProgress)

        val n = progress()
        assertThat(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)).isTrue()
        assertThat(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString()).doesNotContain("곡")
    }
}
