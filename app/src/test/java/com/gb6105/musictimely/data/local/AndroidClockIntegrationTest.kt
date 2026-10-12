package com.gb6105.musictimely.data.local

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** SRS Android §5.4: elapsedRealtime 단조 시계 + BOOT_COUNT. */
@RunWith(AndroidJUnit4::class)
class AndroidClockIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = AndroidClock(context)

    @Test
    fun `monotonic time follows elapsedRealtime`() {
        val before = clock.monotonicNowMs()

        ShadowSystemClock.advanceBy(Duration.ofSeconds(90))

        assertThat(clock.monotonicNowMs() - before).isEqualTo(90_000)
        assertThat(clock.monotonicNowMs()).isEqualTo(SystemClock.elapsedRealtime())
    }

    @Test
    fun `boot count comes from Settings Global`() {
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, 42)

        assertThat(clock.bootCount()).isEqualTo(42)
    }

    @Test
    fun `missing boot count reads as zero`() {
        assertThat(clock.bootCount()).isEqualTo(0)
    }
}
