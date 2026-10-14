package com.gb6105.musictimely.data.shareimage

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.gb6105.musictimely.R
import com.gb6105.musictimely.domain.model.shareimage.ShareCard
import com.gb6105.musictimely.domain.repository.ShareImagePort
import com.gb6105.musictimely.domain.repository.ShareTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * F18 M5–M7 (DEC-A22): keeps one rendered PNG at `cacheDir/share/playlist.png` (overwritten on each prepare, the
 * folder deleted by [clear]) and sends it through [FileProvider] (`${applicationId}.fileprovider`, that folder only).
 * Instagram first when visible (`<queries>` names only its package), else the system share sheet. Failures return
 * null; nothing is logged and no error carries song text.
 */
class AndroidShareImagePort(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val render: (ShareCard) -> Bitmap,
) : ShareImagePort {
    private val dir = File(context.cacheDir, DIR)
    private val file = File(dir, FILE)
    private val authority = "${context.packageName}.fileprovider"

    override suspend fun prepare(card: ShareCard): ByteArray? = withContext(io) {
        try {
            val bitmap = render(card)
            val png = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
            bitmap.recycle()
            dir.mkdirs()
            val temp = File(dir, "$FILE.tmp")
            temp.writeBytes(png)
            if (!temp.renameTo(file)) {
                temp.delete()
                error("rename")
            }
            png
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Out of memory included: the preview shows a short message, the timer and result are untouched (MT-T7).
            file.delete()
            null
        }
    }

    override fun send(): ShareTarget? {
        if (!file.isFile) return null
        val uri = runCatching { FileProvider.getUriForFile(context, authority, file) }.getOrNull() ?: return null
        val instagram = ShareIntents.instagram(uri)
        if (instagram.resolveActivity(context.packageManager) != null && start(instagram)) return ShareTarget.INSTAGRAM
        if (start(ShareIntents.chooser(uri, context.getString(R.string.share_chooser_title)))) return ShareTarget.CHOOSER
        return null
    }

    override suspend fun clear() {
        withContext(io) { dir.deleteRecursively() }
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private companion object {
        const val DIR = "share"
        const val FILE = "playlist.png"
    }
}
