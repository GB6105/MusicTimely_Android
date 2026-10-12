package com.gb6105.musictimely.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gb6105.musictimely.R
import com.gb6105.musictimely.data.audio.NoiseController
import com.gb6105.musictimely.data.notification.ProgressNotificationSlot
import com.gb6105.musictimely.data.notification.SessionNotifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * mediaPlayback foreground service, alive only while app-owned noise plays (SRS Android §7.6, DEC-A06).
 * Headphones unplugged stops the sound with no automatic resume (FR-A058).
 */
@AndroidEntryPoint
class NoisePlaybackService : Service() {

    @Inject lateinit var controller: NoiseController
    @Inject lateinit var progressSlot: ProgressNotificationSlot

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null
    private var lastStartId = 0
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = controller.onBecomingNoisy()
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Foreground first, always: stopping before startForeground would crash (ForegroundServiceDidNotStart).
        // One notification: the session progress one when a session shows it, else a sound-only one (SRS Android §7.6).
        ServiceCompat.startForeground(
            this, SessionNotifier.ID_PROGRESS, progressSlot.latest ?: notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        lastStartId = startId
        // A finished watcher from an earlier play must not be reused (review L6).
        if (watcher?.isActive != true) {
            watcher = scope.launch {
                controller.state.first { !it.playing }
                // Mid-session the progress notification outlives the sound; otherwise it goes with it.
                val keep = progressSlot.latest != null
                ServiceCompat.stopForeground(
                    this@NoisePlaybackService,
                    if (keep) ServiceCompat.STOP_FOREGROUND_DETACH else ServiceCompat.STOP_FOREGROUND_REMOVE,
                )
                // Only the latest start may stop the service; a newer start keeps it alive.
                stopSelf(lastStartId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(noisy)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(SessionNotifier.CHANNEL_PROGRESS, getString(R.string.notification_channel_progress), NotificationManager.IMPORTANCE_LOW)
                .apply { setSound(null, null) },
        )
        return NotificationCompat.Builder(this, SessionNotifier.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_session)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.noise_playing))
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
