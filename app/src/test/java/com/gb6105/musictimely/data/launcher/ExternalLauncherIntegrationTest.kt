package com.gb6105.musictimely.data.launcher

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.source.ExternalSource
import com.gb6105.musictimely.domain.model.source.Provider
import com.gb6105.musictimely.domain.repository.LaunchOutcome
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** F05 열기 폴백 (SRS Android §7.4). FR-A025·A026·A027, QA-A04·QA-19. */
@RunWith(AndroidJUnit4::class)
class ExternalLauncherIntegrationTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val pm = shadowOf(app.packageManager)
    private val launcher = ExternalLauncher(app)
    private val ytm = "com.google.android.apps.youtube.music"
    private val url = "https://music.youtube.com/playlist?list=PL1"
    private val linkSource = ExternalSource("a", Provider.YOUTUBE_MUSIC, ytm, url, "유튜브뮤직", 0)

    private fun install(pkg: String, handlesLinks: Boolean = false) {
        val activity = ComponentName(pkg, "$pkg.Main")
        pm.addActivityIfNotPresent(activity)
        pm.addIntentFilterForActivity(activity, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        if (handlesLinks) {
            pm.addIntentFilterForActivity(
                activity,
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("https")
                    addDataAuthority("music.youtube.com", null)
                },
            )
        }
    }

    private fun installStore() {
        val store = ComponentName("com.android.vending", "com.android.vending.Store")
        pm.addActivityIfNotPresent(store)
        pm.addIntentFilterForActivity(store, IntentFilter(Intent.ACTION_VIEW).apply { addCategory(Intent.CATEGORY_DEFAULT); addDataScheme("market") })
    }

    private fun started(): Intent? = shadowOf(app).nextStartedActivity

    @Test
    fun `opens the saved link inside the music app first`() {
        install(ytm, handlesLinks = true)

        assertThat(launcher.open(linkSource)).isEqualTo(LaunchOutcome.OPENED_LINK)
        val intent = started()!!
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.data).isEqualTo(Uri.parse(url))
        assertThat(intent.`package`).isEqualTo(ytm)
    }

    @Test
    fun `falls back to launching the app when the link cannot be opened`() {
        install(ytm, handlesLinks = false)

        assertThat(launcher.open(linkSource)).isEqualTo(LaunchOutcome.OPENED_APP)
        assertThat(started()!!.`package`).isEqualTo(ytm)
    }

    @Test
    fun `an uninstalled app opens its store listing (QA-A04)`() {
        installStore()

        assertThat(launcher.open(linkSource.copy(linkUri = null))).isEqualTo(LaunchOutcome.OPENED_STORE)
        assertThat(started()!!.data).isEqualTo(Uri.parse("market://details?id=$ytm"))
    }

    @Test
    fun `with nothing to open it asks the user to open the app manually (QA-19)`() {
        assertThat(launcher.open(linkSource)).isEqualTo(LaunchOutcome.MANUAL)
        assertThat(started()).isNull()
    }

    @Test
    fun `lists only installed supported apps for app-only registration (FR-A025)`() {
        install("com.iloen.melon")
        install("com.spotify.music")
        install("com.example.other")

        assertThat(launcher.installedProviders()).containsExactly(Provider.SPOTIFY, Provider.MELON).inOrder()
    }
}
