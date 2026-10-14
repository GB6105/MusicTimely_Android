package com.gb6105.musictimely.data.shareimage

import android.content.ClipData
import android.content.Intent
import android.net.Uri

/**
 * F18 M7 (DEC-A22): a plain `ACTION_SEND` of the PNG with one-time read permission. No Meta app id and no
 * `ADD_TO_STORY`: Instagram shows its own Story/Feed/Direct picker. The fallback is the system share sheet.
 */
object ShareIntents {
    const val INSTAGRAM_PACKAGE = "com.instagram.android"
    const val MIME_PNG = "image/png"

    fun instagram(uri: Uri): Intent = send(uri).setPackage(INSTAGRAM_PACKAGE)

    fun chooser(uri: Uri, title: CharSequence): Intent = Intent.createChooser(send(uri), title)

    private fun send(uri: Uri): Intent = Intent(Intent.ACTION_SEND)
        .setType(MIME_PNG)
        .putExtra(Intent.EXTRA_STREAM, uri)
        // ClipData carries the grant through the chooser to the picked app.
        .apply { clipData = ClipData.newRawUri(null, uri) }
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
