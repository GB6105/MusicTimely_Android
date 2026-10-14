package com.gb6105.musictimely.data.shareimage

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.result.TotalTime
import com.gb6105.musictimely.domain.model.shareimage.ShareCard
import com.gb6105.musictimely.domain.model.shareimage.ShareSong
import com.gb6105.musictimely.domain.repository.ShareTarget
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * F18 M5·M7 임시 파일·FileProvider·전송 폴백 (DEC-A22): `cacheDir/share/`에 PNG 하나(다시 만들면 덮어씀), 지우면 폴더째 삭제,
 * FileProvider는 그 폴더만 노출. 인스타 설치 시 인스타, 아니면 시스템 공유 시트, 둘 다 못 열면 null (예외 0).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidShareImagePortIntegrationTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val card = ShareCard(TotalTime(0, 32, 18), listOf(ShareSong("Soft Landing", "June Archive")), 0)
    private var color = Color.RED
    private var renderFailure: Throwable? = null
    private val port = AndroidShareImagePort(app) { _ ->
        renderFailure?.let { throw it }
        createBitmap(108, 192).apply { eraseColor(color) }
    }
    private val shareDir = File(app.cacheDir, "share")
    private val instagram = "com.instagram.android"

    /** FileProvider caches its roots per authority for the JVM; Robolectric gives every test a new cacheDir. */
    @Before
    fun resetFileProviderRoots() {
        val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.get(null) as MutableMap<*, *>
        cache.clear()
    }

    private fun installInstagram() {
        val activity = ComponentName(instagram, "$instagram.ShareHandler")
        val pm = shadowOf(app.packageManager)
        pm.addActivityIfNotPresent(activity)
        pm.addIntentFilterForActivity(activity, IntentFilter(Intent.ACTION_SEND).apply { addCategory(Intent.CATEGORY_DEFAULT); addDataType("image/*") })
    }

    private fun started(): Intent? = shadowOf(app).nextStartedActivity
    private fun Intent.stream(): Uri? = IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)

    @Test
    fun `prepare writes one png into cache share folder and returns its bytes`() = runTest {
        val bytes = port.prepare(card)!!

        assertThat(shareDir.listFiles()!!.map { it.name }).containsExactly("playlist.png")
        val file = shareDir.listFiles()!!.single()
        assertThat(file.readBytes()).isEqualTo(bytes)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertThat(decoded.width to decoded.height).isEqualTo(108 to 192)
        assertThat(bytes.copyOfRange(1, 4).decodeToString()).isEqualTo("PNG")
    }

    @Test
    fun `preparing again overwrites the same file`() = runTest {
        port.prepare(card)
        color = Color.BLUE
        val second = port.prepare(card)!!

        assertThat(shareDir.listFiles()!!.map { it.name }).containsExactly("playlist.png")
        assertThat(File(shareDir, "playlist.png").readBytes()).isEqualTo(second)
        val decoded = BitmapFactory.decodeByteArray(second, 0, second.size)
        assertThat(decoded.getPixel(0, 0)).isEqualTo(Color.BLUE)
    }

    @Test
    fun `a render failure such as out of memory returns null and leaves no image (MT-T7)`() = runTest {
        port.prepare(card)
        renderFailure = OutOfMemoryError("test")

        assertThat(port.prepare(card)).isNull()
        assertThat(File(shareDir, "playlist.png").exists()).isFalse()
        assertThat(port.send()).isNull()
    }

    @Test
    fun `clear deletes the share folder with its image (result closed, DEC-A18 moment)`() = runTest {
        port.prepare(card)

        port.clear()

        assertThat(shareDir.exists()).isFalse()
    }

    @Test
    fun `the file provider serves only the share folder`() = runTest {
        port.prepare(card)
        val authority = "${app.packageName}.fileprovider"

        assertThat(FileProvider.getUriForFile(app, authority, File(shareDir, "playlist.png")).authority).isEqualTo(authority)
        val outside = File(app.cacheDir, "other.png").apply { writeText("x") }
        assertThrows(IllegalArgumentException::class.java) { FileProvider.getUriForFile(app, authority, outside) }
        val noBackup = File(app.noBackupFilesDir, "session_playlists.json").apply { parentFile!!.mkdirs(); writeText("x") }
        assertThrows(IllegalArgumentException::class.java) { FileProvider.getUriForFile(app, authority, noBackup) }
    }

    @Test
    fun `with Instagram installed the image goes to Instagram with read permission (DEC-A22)`() = runTest {
        installInstagram()
        port.prepare(card)

        assertThat(port.send()).isEqualTo(ShareTarget.INSTAGRAM)

        val intent = started()!!
        assertThat(intent.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(intent.type).isEqualTo("image/png")
        assertThat(intent.`package`).isEqualTo(instagram)
        assertThat(intent.stream()!!.authority).isEqualTo("${app.packageName}.fileprovider")
        assertThat(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    @Test
    fun `without Instagram the system share sheet opens with the same image`() = runTest {
        port.prepare(card)

        assertThat(port.send()).isEqualTo(ShareTarget.CHOOSER)

        val chooser = started()!!
        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        val inner = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!
        assertThat(inner.stream()!!.path).isEqualTo("/share/playlist.png")
        assertThat(inner.`package`).isNull()
    }

    @Test
    fun `when no app can open it send returns null without throwing`() = runTest {
        port.prepare(card)
        shadowOf(app).checkActivities(true)

        assertThat(port.send()).isNull()
    }

    @Test
    fun `nothing is sent before an image is prepared`() {
        installInstagram()

        assertThat(port.send()).isNull()
        assertThat(started()).isNull()
    }

    @Test
    fun `a cleared image cannot be sent (result closed)`() = runTest {
        installInstagram()
        port.prepare(card)
        port.clear()

        assertThat(port.send()).isNull()
        assertThat(started()).isNull()
    }
}
