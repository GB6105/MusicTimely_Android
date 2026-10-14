package com.gb6105.musictimely.ui.shareimage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import androidx.annotation.FontRes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.gb6105.musictimely.R
import com.gb6105.musictimely.domain.model.result.TotalTime
import com.gb6105.musictimely.domain.model.shareimage.ShareCard
import com.gb6105.musictimely.ui.component.CoverMotif
import com.gb6105.musictimely.ui.component.drawCoverMotif
import com.gb6105.musictimely.ui.theme.MtColors
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.ui.graphics.Canvas as ComposeCanvas

/**
 * F18 M5 share image (DEC-A22): a 1080×1920 light canvas in result A's tone — "MUSIC TIMELY", "이번 플레이리스트",
 * the total time, then up to eight songs (title and artist, one line each, ellipsized) with the app cover motif,
 * then "외 N곡". Drawn with Android Canvas so it is deterministic. Only [ShareCard] goes in: no task title, date,
 * estimate or album art (DEC-A21).
 */
class ShareImageRenderer(private val context: Context) {
    data class Row(val title: String, val artist: String?)

    data class Texts(val mark: String, val title: String, val totalTime: String, val rows: List<Row>, val more: String?) {
        fun all(): List<String> = listOfNotNull(mark, title, totalTime, more) + rows.flatMap { listOfNotNull(it.title, it.artist) }
    }

    private val plexRegular by lazy { font(R.font.ibm_plex_sans_kr_regular) }
    private val plexMedium by lazy { font(R.font.ibm_plex_sans_kr_medium) }
    private val plexBold by lazy { font(R.font.ibm_plex_sans_kr_bold) }
    private val montserrat by lazy { font(R.font.montserrat_variable) }

    private val markPaint by lazy {
        paint(montserrat, 34f, MtColors.Icon).apply { letterSpacing = 0.12f; fontVariationSettings = "'wght' 600" }
    }
    private val titlePaint by lazy { paint(plexBold, 84f, MtColors.Light.ink) }
    private val timePaint by lazy { paint(plexRegular, 44f, MtColors.Light.muted) }
    private val songPaint by lazy { paint(plexMedium, 46f, MtColors.Light.ink) }
    private val artistPaint by lazy { paint(plexRegular, 36f, MtColors.Light.muted) }
    private val morePaint by lazy { paint(plexMedium, 40f, MtColors.Light.muted) }

    fun texts(card: ShareCard): Texts {
        val res = context.resources
        return Texts(
            mark = res.getString(R.string.share_image_mark),
            title = res.getString(R.string.result_playlist_title),
            totalTime = totalTimeText(card.totalTime),
            rows = card.songs.map { song ->
                Row(oneLine(song.title, songPaint), song.artist?.let { oneLine(it, artistPaint) })
            },
            more = card.moreCount.takeIf { it > 0 }?.let { res.getString(R.string.share_image_more, it) },
        )
    }

    fun render(card: ShareCard): Bitmap {
        val texts = texts(card)
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(MtColors.Light.background.toArgb())
        canvas.drawText(texts.mark, PADDING.toFloat(), 200f, markPaint)
        canvas.drawText(texts.title, PADDING.toFloat(), 320f, titlePaint)
        canvas.drawText(texts.totalTime, PADDING.toFloat(), 400f, timePaint)
        texts.rows.forEachIndexed { index, row -> drawRow(canvas, index, row) }
        texts.more?.let { canvas.drawText(it, TEXT_LEFT.toFloat(), (rowTop(texts.rows.size) + 70).toFloat(), morePaint) }
        return bitmap
    }

    private fun drawRow(canvas: AndroidCanvas, index: Int, row: Row) {
        val top = coverTop(index).toFloat()
        canvas.withSave {
            translate(PADDING.toFloat(), top)
            clipPath(Path().apply { addRoundRect(RectF(0f, 0f, COVER.toFloat(), COVER.toFloat()), COVER_RADIUS, COVER_RADIUS, Path.Direction.CW) })
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, ComposeCanvas(this), Size(COVER.toFloat(), COVER.toFloat())) {
                drawCoverMotif(CoverMotif.variantFor(index))
            }
        }
        val rowTop = rowTop(index)
        if (row.artist == null) {
            canvas.drawText(row.title, TEXT_LEFT.toFloat(), (rowTop + 86).toFloat(), songPaint)
        } else {
            canvas.drawText(row.title, TEXT_LEFT.toFloat(), (rowTop + 62).toFloat(), songPaint)
            canvas.drawText(row.artist, TEXT_LEFT.toFloat(), (rowTop + 112).toFloat(), artistPaint)
        }
    }

    /** One line, cut with "…" to the text column (titles never wrap or leave the image). */
    private fun oneLine(text: String, paint: TextPaint): String {
        val line = text.replace('\n', ' ')
        if (paint.measureText(line) <= TEXT_WIDTH) return line
        val fits = paint.breakText(line, true, TEXT_WIDTH - paint.measureText(ELLIPSIS), null)
        return line.take(fits).trimEnd() + ELLIPSIS
    }

    private fun totalTimeText(t: TotalTime): String = when (t.shape) {
        TotalTime.Shape.SECONDS -> context.getString(R.string.result_total_time_s, t.seconds)
        TotalTime.Shape.MINUTES_SECONDS -> context.getString(R.string.result_total_time_ms, t.minutes, t.seconds)
        TotalTime.Shape.HOURS_MINUTES_SECONDS -> context.getString(R.string.result_total_time_hms, t.hours, t.minutes, t.seconds)
    }

    private fun font(@FontRes id: Int): Typeface =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: Typeface.DEFAULT

    private fun paint(typeface: Typeface, size: Float, color: Color) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = size
        this.color = color.toArgb()
    }

    companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1920
        const val PADDING = 96
        const val COVER = 104
        private const val COVER_RADIUS = 18f
        private const val ELLIPSIS = "…"
        private const val ROWS_TOP = 500
        private const val ROW_HEIGHT = 140
        private const val TEXT_LEFT = PADDING + COVER + 36
        private const val TEXT_WIDTH = WIDTH - PADDING - TEXT_LEFT

        private fun rowTop(row: Int): Int = ROWS_TOP + row * ROW_HEIGHT

        /** Top edge of row [row]'s cover tile. */
        fun coverTop(row: Int): Int = rowTop(row) + (ROW_HEIGHT - COVER) / 2
    }
}
