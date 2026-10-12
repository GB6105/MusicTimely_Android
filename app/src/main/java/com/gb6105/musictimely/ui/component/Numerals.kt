package com.gb6105.musictimely.ui.component

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.gb6105.musictimely.ui.theme.MtFonts

/** Korean in Plex, digits and Latin in Montserrat within one sentence (docs/design/README.md 폰트). */
fun withNumerals(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val latin = text[i].isLatinOrDigit()
        var j = i
        while (j < text.length && text[j].isLatinOrDigit() == latin) j++
        val run = text.substring(i, j)
        if (latin) withStyle(SpanStyle(fontFamily = MtFonts.Montserrat, fontFeatureSettings = "tnum")) { append(run) } else append(run)
        i = j
    }
}

private fun Char.isLatinOrDigit() = this in '0'..'9' || this in 'A'..'Z' || this in 'a'..'z' || this == '.' || this == ':'
