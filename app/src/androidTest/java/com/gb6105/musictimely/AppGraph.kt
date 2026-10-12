package com.gb6105.musictimely

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gb6105.musictimely.di.DeviceTestEntryPoint
import dagger.hilt.android.EntryPointAccessors

/** The installed app's real Hilt singletons. */
val appGraph: DeviceTestEntryPoint
    get() = EntryPointAccessors.fromApplication(ApplicationProvider.getApplicationContext<Context>(), DeviceTestEntryPoint::class.java)

/** Polls [condition] up to [timeoutMs]; OS work (alarms, services, audio) is asynchronous. */
fun eventually(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
    val end = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < end) {
        if (condition()) return true
        Thread.sleep(100)
    }
    return condition()
}
