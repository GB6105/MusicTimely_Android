package com.gb6105.musictimely.ui.component

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gb6105.musictimely.R
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** Design icons (docs/design/assets/icon-*.svg) as vector drawables: viewport size kept, 2dp stroke, round caps. */
@RunWith(AndroidJUnit4::class)
class IconResourcesIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val density = context.resources.displayMetrics.density

    private val icons = mapOf(
        "back" to (R.drawable.ic_mt_back to 24),
        "chevron" to (R.drawable.ic_mt_chevron to 16),
        "close" to (R.drawable.ic_mt_close to 24),
        "gear" to (R.drawable.ic_mt_gear to 24),
        "more" to (R.drawable.ic_mt_more to 24),
        "note" to (R.drawable.ic_mt_note to 22),
        "pause" to (R.drawable.ic_mt_pause to 24),
        "plus" to (R.drawable.ic_mt_plus to 24),
        "stop" to (R.drawable.ic_mt_stop to 24),
    )

    @Test
    fun `all nine design icons load at their design size`() {
        icons.forEach { (name, pair) ->
            val (id, size) = pair
            val drawable = ContextCompat.getDrawable(context, id)
            assertWithMessage(name).that(drawable).isNotNull()
            assertWithMessage(name).that(drawable!!.intrinsicWidth).isEqualTo((size * density).toInt())
            assertWithMessage(name).that(drawable.intrinsicHeight).isEqualTo((size * density).toInt())
        }
    }

    @Test
    fun `every icon path is a 2dp round stroke without fill`() {
        icons.forEach { (name, pair) ->
            val parser = context.resources.getXml(pair.first)
            var paths = 0
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.name != "path") continue
                paths++
                val attrs = (0 until parser.attributeCount).associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }
                assertWithMessage(name).that(attrs["strokeWidth"]?.toFloat()).isEqualTo(2f)
                assertWithMessage(name).that(attrs["strokeLineCap"]).isAnyOf("1", "round")
                assertWithMessage(name).that(attrs["fillColor"]).isNull()
            }
            assertWithMessage(name).that(paths).isAtLeast(1)
        }
    }
}
