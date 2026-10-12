package com.gb6105.musictimely.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Explicit-only receiver (no intent filter) for the end alarm and the progress notification actions. */
@AndroidEntryPoint
class SessionActionReceiver : BroadcastReceiver() {

    @Inject lateinit var handler: SessionActionHandler

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                handler.handle(intent.action)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_KEY = "key"
        const val ACTION_END_ALARM = "com.gb6105.musictimely.action.END_ALARM"
        const val ACTION_PAUSE = "com.gb6105.musictimely.action.PAUSE"
        const val ACTION_RESUME = "com.gb6105.musictimely.action.RESUME"
        const val ACTION_FINISH = "com.gb6105.musictimely.action.FINISH"
        const val ACTION_PROGRESS_DISMISSED = "com.gb6105.musictimely.action.PROGRESS_DISMISSED"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
