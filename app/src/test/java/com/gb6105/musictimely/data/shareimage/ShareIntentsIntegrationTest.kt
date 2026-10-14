package com.gb6105.musictimely.data.shareimage

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** F18 M7 인텐트 구성 (DEC-A22): ACTION_SEND + image/png + EXTRA_STREAM + 읽기 권한, 인스타 패키지 지정 / 폴백은 시스템 공유 시트. */
@RunWith(AndroidJUnit4::class)
class ShareIntentsIntegrationTest {

    private val uri = Uri.parse("content://com.gb6105.musictimely.fileprovider/share/playlist.png")

    private fun assertSendsImage(intent: Intent) {
        assertThat(intent.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(intent.type).isEqualTo("image/png")
        assertThat(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)).isEqualTo(uri)
        assertThat(intent.clipData!!.getItemAt(0).uri).isEqualTo(uri)
        assertThat(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        assertThat(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION).isEqualTo(0)
    }

    @Test
    fun `the Instagram intent sends the png to the Instagram package only, no Meta app id (DEC-A22)`() {
        val intent = ShareIntents.instagram(uri)

        assertSendsImage(intent)
        assertThat(intent.`package`).isEqualTo("com.instagram.android")
        assertThat(intent.action).isNotEqualTo("com.instagram.share.ADD_TO_STORY")
        assertThat(intent.extras!!.keySet()).containsExactly(Intent.EXTRA_STREAM)
    }

    @Test
    fun `the fallback is the system share sheet around the same png, for any app`() {
        val chooser = ShareIntents.chooser(uri, "플레이리스트 공유하기")
        val inner = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!

        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(chooser.getCharSequenceExtra(Intent.EXTRA_TITLE).toString()).isEqualTo("플레이리스트 공유하기")
        assertSendsImage(inner)
        assertThat(inner.`package`).isNull()
    }
}
