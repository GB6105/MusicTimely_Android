package com.gb6105.musictimely.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.gb6105.musictimely.data.effect.TimerEffectHandler
import com.gb6105.musictimely.domain.model.timer.TimerEffect
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.receiver.SessionActionReceiver

/**
 * DEC-A06: inexact `setAndAllowWhileIdle` on ELAPSED_REALTIME_WAKEUP. No exact-alarm permission.
 * One request code, so scheduling again replaces the pending alarm (key = sessionId:targetRevision:end).
 */
class EndAlarmScheduler(private val context: Context) : TimerEffectHandler {

    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    override fun handles(effect: TimerEffect): Boolean =
        effect is TimerEffect.ScheduleEndAlarm || effect == TimerEffect.CancelEndAlarm

    override suspend fun handle(session: TimerSession, effect: TimerEffect) {
        when (effect) {
            is TimerEffect.ScheduleEndAlarm -> alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                effect.atMonoMs,
                pendingIntent("${session.sessionId}:${effect.targetRevision}:end"),
            )
            TimerEffect.CancelEndAlarm -> pendingIntent(key = null).let {
                alarmManager.cancel(it)
                it.cancel()
            }
            else -> Unit
        }
    }

    private fun pendingIntent(key: String?): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_END_ALARM,
        Intent(context, SessionActionReceiver::class.java)
            .setAction(SessionActionReceiver.ACTION_END_ALARM)
            .putExtra(SessionActionReceiver.EXTRA_KEY, key),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val REQUEST_END_ALARM = 1
    }
}
