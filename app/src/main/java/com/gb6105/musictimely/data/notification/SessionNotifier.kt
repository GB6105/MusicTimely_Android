package com.gb6105.musictimely.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gb6105.musictimely.MainActivity
import com.gb6105.musictimely.R
import com.gb6105.musictimely.data.effect.TimerEffectHandler
import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.model.translation.SongTranslation
import com.gb6105.musictimely.domain.repository.ClockPort
import com.gb6105.musictimely.receiver.SessionActionReceiver

/**
 * SCR-14 progress notification and the end notification (SRS Android §7.6).
 * Never shows the task title or a song, never offers a music control (FR-A061, FR-A062, MT-N2·N3).
 * Without POST_NOTIFICATIONS nothing is posted; the session continues (NOTIFICATION_DENIED).
 */
class SessionNotifier(
    private val context: Context,
    private val clock: ClockPort,
    private val dismissal: ProgressDismissal,
    private val isAppInForeground: () -> Boolean,
    private val slot: ProgressNotificationSlot = ProgressNotificationSlot(),
    private val showSongs: () -> Boolean = { true },
) : TimerEffectHandler {

    private val manager get() = context.getSystemService(NotificationManager::class.java)

    override fun handles(effect: TimerEffect): Boolean =
        effect == TimerEffect.ShowProgress || effect == TimerEffect.ShowLimitReached || effect == TimerEffect.ClearNotifications

    override suspend fun handle(session: TimerSession, effect: TimerEffect) {
        when (effect) {
            TimerEffect.ShowProgress -> postProgress(session)
            TimerEffect.ShowLimitReached -> {
                postProgress(session)
                if (!isAppInForeground()) post(ID_END) { end() }
            }
            TimerEffect.ClearNotifications -> {
                slot.latest = null
                manager.cancel(ID_PROGRESS)
                manager.cancel(ID_END)
            }
            else -> Unit
        }
    }

    private fun postProgress(session: TimerSession) {
        val foreground = isAppInForeground()
        if (dismissal.isDismissed && !foreground) {
            // Dismissed: the sound service must not bring a stale copy back (review M2).
            slot.latest = null
            return
        }
        if (foreground) dismissal.reset()
        if (!canPost()) return
        ensureChannels()
        val notification = progress(session).build()
        slot.latest = notification
        manager.notify(ID_PROGRESS, notification)
    }

    private inline fun post(id: Int, build: () -> NotificationCompat.Builder) {
        if (!canPost()) return
        ensureChannels()
        manager.notify(id, build().build())
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun progress(session: TimerSession): NotificationCompat.Builder {
        val now = clock.monotonicNowMs()
        val builder = base(CHANNEL_PROGRESS).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setDeleteIntent(action(SessionActionReceiver.ACTION_PROGRESS_DISMISSED))
        when (session.state) {
            TimerState.RUNNING -> {
                val remaining = session.remainingMs(now)
                if (remaining != null) {
                    val target = session.allocation.currentTargetMs ?: 0
                    val songs = SongTranslation.remainingSongs(target, session.elapsedMs(now), session.allocation.displayUnitMs)
                    builder.setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true)
                        .setWhen(clock.wallNowEpochMs() + remaining)
                        .setContentText(
                            when {
                                !showSongs() -> context.getString(R.string.notification_running)
                                session.allocation.singleLoop -> context.getString(R.string.notification_remaining_loops, songs)
                                else -> context.getString(R.string.notification_remaining_songs, songs)
                            },
                        )
                } else {
                    builder.setUsesChronometer(true).setShowWhen(true)
                        .setWhen(clock.wallNowEpochMs() - session.elapsedMs(now))
                        .setContentText(context.getString(R.string.notification_running))
                }
                builder.addAction(0, context.getString(R.string.notification_action_pause), action(SessionActionReceiver.ACTION_PAUSE))
            }
            TimerState.PAUSED -> builder.setShowWhen(false)
                .setContentText(context.getString(R.string.session_paused_title))
                .addAction(0, context.getString(R.string.notification_action_resume), action(SessionActionReceiver.ACTION_RESUME))
            else -> builder.setShowWhen(false).setContentText(context.getString(R.string.session_limit_reached))
        }
        return builder.addAction(0, context.getString(R.string.notification_action_finish), action(SessionActionReceiver.ACTION_FINISH))
    }

    private fun end() = base(CHANNEL_END).setAutoCancel(true)
        .setContentText(context.getString(R.string.session_limit_reached))

    private fun base(channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_session)
        .setContentTitle(context.getString(R.string.notification_title))
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(openApp())

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, REQUEST_OPEN,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, SessionActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.notification_channel_progress), NotificationManager.IMPORTANCE_LOW)
                .apply {
                    setSound(null, null)
                    enableVibration(false)
                },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_END, context.getString(R.string.notification_channel_end), NotificationManager.IMPORTANCE_DEFAULT)
                .apply {
                    setSound(null, null)
                    enableVibration(true)
                },
        )
    }

    companion object {
        const val CHANNEL_PROGRESS = "session_progress"
        const val CHANNEL_END = "session_end"
        const val ID_PROGRESS = 1
        private const val ID_END = 2
        private const val REQUEST_OPEN = 10
    }
}
