package com.gb6105.musictimely.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gb6105.musictimely.R

/** Design tokens from docs/design/README.md (Figma Design System 0:1). Navy theme awaits the V1 decision. */
data class MtPalette(val background: Color, val surface: Color, val field: Color, val ink: Color, val muted: Color)

object MtColors {
    val Light = MtPalette(
        background = Color(0xFFECECEC),
        surface = Color(0xFFEEEEEE),
        field = Color(0xFFFFFFFF),
        ink = Color(0xFF26262C),
        muted = Color(0xFF64656E),
    )
    val Dark = MtPalette(
        background = Color(0xFF26272C),
        surface = Color(0xFF26272C),
        field = Color(0xFF303138),
        ink = Color(0xFFF3F3F5),
        muted = Color(0xFFB4B5BE),
    )
    val Orange = Color(0xFFFF9C3F)
    val Coral = Color(0xFFFF7544)
    val Red = Color(0xFFFF3B4D)
    val Icon = Color(0xFFF5474F)
    val Navy = Color(0xFF1B1B33)
    val Charcoal = Color(0xFF232429)
    val Vinyl = Color(0xFF0B0B0D)
    val DarkData = Color(0xFF1A1B1F)

    /** CTA, selection and progress emphasis only; screens stay neutral. */
    val Sunset = listOf(Orange, Coral, Red)
    val SunsetStops: Array<Pair<Float, Color>> = arrayOf(0f to Orange, 0.48f to Coral, 1f to Red)
}

/** Korean text in IBM Plex Sans KR, Latin and numerals in Montserrat (variable). */
@OptIn(ExperimentalTextApi::class)
object MtFonts {
    val Plex = FontFamily(
        Font(R.font.ibm_plex_sans_kr_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_sans_kr_medium, FontWeight.Medium),
        Font(R.font.ibm_plex_sans_kr_bold, FontWeight.Bold),
    )
    val Montserrat = FontFamily(
        listOf(300, 400, 500, 600).map { weight ->
            Font(
                R.font.montserrat_variable,
                FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            )
        },
    )
}

object MtType {
    private fun plex(size: Int, line: Int, weight: FontWeight) = TextStyle(
        fontFamily = MtFonts.Plex, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
    )

    private fun montserrat(size: Int, line: Int, weight: FontWeight, tabular: Boolean = false) = TextStyle(
        fontFamily = MtFonts.Montserrat, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
        fontFeatureSettings = if (tabular) "tnum" else null,
    )

    val screenTitle = plex(24, 34, FontWeight.Bold)
    val resultTitle = plex(26, 37, FontWeight.Bold)
    val sectionTitle = plex(21, 30, FontWeight.Bold)
    val panelTitle = plex(22, 31, FontWeight.Bold)
    val songAmount = plex(32, 45, FontWeight.Medium)
    val dialNumber = montserrat(38, 54, FontWeight.Light, tabular = true)
    val chipNumber = montserrat(24, 34, FontWeight.Light, tabular = true)
    val metric = montserrat(34, 48, FontWeight.Medium, tabular = true)
    val timerNumber = montserrat(15, 21, FontWeight.Medium, tabular = true)
    /** A2 time card clocks and song progress. */
    val timeCard = montserrat(28, 36, FontWeight.Normal, tabular = true)
    val button = plex(17, 24, FontWeight.Medium)
    val body = plex(15, 21, FontWeight.Medium)
    val bodySmall = plex(13, 19, FontWeight.Normal)
    val caption = plex(11, 16, FontWeight.Normal)
    val unitLabel = montserrat(10, 14, FontWeight.SemiBold)
}

object MtDimens {
    /** Figma 44pt raised to Android's 48dp minimum (SRS Android §11). */
    val MinTouch: Dp = 48.dp
    val PrimaryButtonHeight: Dp = 58.dp
    val SecondaryButtonHeight: Dp = 46.dp
    val ScreenPadding: Dp = 24.dp
    val CardPadding: Dp = 20.dp
    val CardRadius: Dp = 30.dp
    val PanelRadius: Dp = 26.dp
    val FieldHeight: Dp = 58.dp
    val TimeChip: Dp = 70.dp
    val Vinyl: Dp = 286.dp
    val VinylLabel: Dp = 100.dp
    val CircleControl: Dp = 48.dp
    val TimeChipGap: Dp = 21.dp
    val SessionControlSmall: Dp = 60.dp
    val SessionControlLarge: Dp = 84.dp
    val FieldRadius: Dp = 29.dp
    val ContentWidth: Dp = 350.dp
    val ButtonWidth: Dp = 342.dp
    val TranslationCardHeight: Dp = 188.dp
    val ProgressPanelHeight: Dp = 134.dp
    val MusicAppRowHeight: Dp = 68.dp
    val MusicAppRowRadius: Dp = 34.dp
    val PrimaryButtonRadius: Dp = 29.dp
    val SecondaryButtonRadius: Dp = 23.dp
    val ProgressCell: Pair<Dp, Dp> = 38.dp to 10.dp
    val ProgressCellGap: Dp = 5.dp
    val ProgressTrackHeight: Dp = 20.dp
    val ProgressTrackRadius: Dp = 5.dp
}

/** Spacing scale (docs/design/README.md). */
object MtSpacing {
    val scale: List<Dp> = listOf(4.dp, 8.dp, 12.dp, 16.dp, 20.dp, 24.dp, 32.dp, 48.dp)
}

/** Corner radii: 16 / 24 / 26 / 30 / pill. */
object MtRadius {
    val Small: Dp = 16.dp
    val Medium: Dp = 24.dp
    val Panel: Dp = 26.dp
    val Card: Dp = 30.dp
    val Pill: Dp = 999.dp
}

