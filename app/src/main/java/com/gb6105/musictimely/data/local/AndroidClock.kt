package com.gb6105.musictimely.data.local

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.gb6105.musictimely.domain.repository.ClockPort

/** SRS Android §5.4: `elapsedRealtime` (includes deep sleep) and `Settings.Global.BOOT_COUNT`. */
class AndroidClock(private val context: Context) : ClockPort {
    override fun monotonicNowMs(): Long = SystemClock.elapsedRealtime()

    override fun wallNowEpochMs(): Long = System.currentTimeMillis()

    override fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
}
