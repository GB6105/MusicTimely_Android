package com.gb6105.musictimely.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.domain.model.playlist.Artwork
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DEC-A21 앨범 아트 읽기·인코딩: 메타데이터 비트맵만(ALBUM_ART → ART → DISPLAY_ICON), `*_URI`는 읽지 않음,
 * 긴 변 120px 정사각 크롭, API 30+ WebP lossy · 29 JPEG, 24KB 초과·실패는 null.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkEncoderIntegrationTest {

    private fun solid(w: Int, h: Int, color: Int) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun decode(bytes: ByteArray?): Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes!!.size)

    private fun isWebp(b: ByteArray) = String(b, 0, 4, Charsets.US_ASCII) == "RIFF" && String(b, 8, 4, Charsets.US_ASCII) == "WEBP"

    private fun isJpeg(b: ByteArray) = b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()

    @Test
    fun `a large cover becomes a 120 px square webp within 24 KB`() {
        val bytes = ArtworkEncoder.encode(solid(600, 300, Color.RED))

        assertThat(bytes).isNotNull()
        assertThat(bytes!!.size).isAtMost(Artwork.MAX_BYTES)
        assertThat(isWebp(bytes)).isTrue()
        val image = decode(bytes)
        assertThat(image.width to image.height).isEqualTo(120 to 120)
    }

    @Test
    fun `a small cover is cropped square but never upscaled`() {
        val image = decode(ArtworkEncoder.encode(solid(50, 80, Color.RED)))

        assertThat(image.width to image.height).isEqualTo(50 to 50)
    }

    @Test
    @Config(sdk = [29])
    fun `below API 30 the cover is encoded as jpeg`() {
        val bytes = ArtworkEncoder.encode(solid(200, 200, Color.RED))

        assertThat(isJpeg(bytes!!)).isTrue()
    }

    @Test
    fun `a result above the size limit is dropped`() {
        assertThat(ArtworkEncoder.encode(solid(200, 200, Color.RED), maxBytes = 10)).isNull()
    }

    @Test
    fun `an unusable bitmap is no artwork and never throws`() {
        val recycled = solid(200, 200, Color.RED).apply { recycle() }

        assertThat(ArtworkEncoder.encode(recycled)).isNull()
    }

    @Test
    fun `album art is preferred over art and art over the display icon`() {
        val all = MediaMetadata.Builder()
            .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, solid(64, 64, Color.GREEN))
            .putBitmap(MediaMetadata.METADATA_KEY_ART, solid(64, 64, Color.BLUE))
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, solid(64, 64, Color.RED))
            .build()
        val artAndIcon = MediaMetadata.Builder()
            .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, solid(64, 64, Color.GREEN))
            .putBitmap(MediaMetadata.METADATA_KEY_ART, solid(64, 64, Color.BLUE))
            .build()
        val iconOnly = MediaMetadata.Builder().putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, solid(64, 64, Color.GREEN)).build()

        assertThat(dominant(ArtworkEncoder.fromMetadata(all))).isEqualTo("red")
        assertThat(dominant(ArtworkEncoder.fromMetadata(artAndIcon))).isEqualTo("blue")
        assertThat(dominant(ArtworkEncoder.fromMetadata(iconOnly))).isEqualTo("green")
    }

    @Test
    fun `art published only as a uri is not fetched`() {
        val uriOnly = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, "content://other.app/art/1")
            .putString(MediaMetadata.METADATA_KEY_ART_URI, "https://example.com/a.jpg")
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, "content://other.app/icon/1")
            .build()

        assertThat(ArtworkEncoder.fromMetadata(uriOnly)).isNull()
    }

    private fun dominant(bytes: ByteArray?): String {
        val p = decode(bytes).getPixel(32, 32)
        return when (maxOf(Color.red(p), Color.green(p), Color.blue(p))) {
            Color.red(p) -> "red"
            Color.green(p) -> "green"
            else -> "blue"
        }
    }
}
