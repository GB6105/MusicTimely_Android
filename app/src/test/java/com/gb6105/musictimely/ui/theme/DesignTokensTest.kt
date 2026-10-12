package com.gb6105.musictimely.ui.theme

import com.google.common.truth.Truth.assertWithMessage
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** F17 디자인 토큰 = docs/design/README.md (Figma Design System 0:1). */
class DesignTokensTest {

    @Test
    fun `light palette matches the design system`() {
        with(MtColors.Light) {
            assertThat(background).isEqualTo(Color(0xFFECECEC))
            assertThat(surface).isEqualTo(Color(0xFFEEEEEE))
            assertThat(field).isEqualTo(Color(0xFFFFFFFF))
            assertThat(ink).isEqualTo(Color(0xFF26262C))
            assertThat(muted).isEqualTo(Color(0xFF64656E))
        }
    }

    @Test
    fun `dark palette matches the design system`() {
        with(MtColors.Dark) {
            assertThat(background).isEqualTo(Color(0xFF26272C))
            assertThat(field).isEqualTo(Color(0xFF303138))
            assertThat(ink).isEqualTo(Color(0xFFF3F3F5))
            assertThat(muted).isEqualTo(Color(0xFFB4B5BE))
        }
    }

    @Test
    fun `accent and contrast colors match`() {
        assertThat(MtColors.Orange).isEqualTo(Color(0xFFFF9C3F))
        assertThat(MtColors.Coral).isEqualTo(Color(0xFFFF7544))
        assertThat(MtColors.Red).isEqualTo(Color(0xFFFF3B4D))
        assertThat(MtColors.Icon).isEqualTo(Color(0xFFF5474F))
        assertThat(MtColors.Navy).isEqualTo(Color(0xFF1B1B33))
        assertThat(MtColors.Charcoal).isEqualTo(Color(0xFF232429))
        assertThat(MtColors.Vinyl).isEqualTo(Color(0xFF0B0B0D))
        assertThat(MtColors.DarkData).isEqualTo(Color(0xFF1A1B1F))
        assertThat(MtColors.Sunset).containsExactly(MtColors.Orange, MtColors.Coral, MtColors.Red).inOrder()
    }

    @Test
    fun `body and muted text keep 4_5 to 1 contrast on their backgrounds (SRS Android 11)`() {
        listOf(MtColors.Light, MtColors.Dark).forEach { p ->
            assertThat(contrast(p.ink, p.background)).isAtLeast(4.5)
            assertThat(contrast(p.muted, p.background)).isAtLeast(4.5)
        }
    }

    @Test
    fun `type scale matches all 12 design roles (docs design README)`() {
        val plex = MtFonts.Plex
        val montserrat = MtFonts.Montserrat
        data class Role(val style: TextStyle, val size: Int, val line: Int, val weight: FontWeight, val family: FontFamily)
        val roles = mapOf(
            "화면 제목" to Role(MtType.screenTitle, 24, 34, FontWeight.Bold, plex),
            "결과 작업 제목" to Role(MtType.resultTitle, 26, 37, FontWeight.Bold, plex),
            "섹션 제목" to Role(MtType.sectionTitle, 21, 30, FontWeight.Bold, plex),
            "진행 패널 제목" to Role(MtType.panelTitle, 22, 31, FontWeight.Bold, plex),
            "곡 수 강조" to Role(MtType.songAmount, 32, 45, FontWeight.Medium, plex),
            "다이얼 숫자" to Role(MtType.dialNumber, 38, 54, FontWeight.Light, montserrat),
            "칩 숫자" to Role(MtType.chipNumber, 24, 34, FontWeight.Light, montserrat),
            "결과 지표" to Role(MtType.metric, 34, 48, FontWeight.Medium, montserrat),
            "입력·버튼" to Role(MtType.button, 17, 24, FontWeight.Medium, plex),
            "본문" to Role(MtType.body, 15, 21, FontWeight.Medium, plex),
            "보조·캡션" to Role(MtType.caption, 11, 16, FontWeight.Normal, plex),
            "단위 라벨" to Role(MtType.unitLabel, 10, 14, FontWeight.SemiBold, montserrat),
        )
        roles.forEach { (name, r) ->
            assertWithMessage(name).that(r.style.fontSize).isEqualTo(r.size.sp)
            assertWithMessage(name).that(r.style.lineHeight).isEqualTo(r.line.sp)
            assertWithMessage(name).that(r.style.fontWeight).isEqualTo(r.weight)
            assertWithMessage(name).that(r.style.fontFamily).isEqualTo(r.family)
        }
        listOf(MtType.dialNumber, MtType.chipNumber, MtType.metric, MtType.timerNumber).forEach {
            assertThat(it.fontFeatureSettings).isEqualTo("tnum")
        }
    }

    @Test
    fun `sizes follow the design with touch targets raised to 48dp`() {
        assertThat(MtDimens.MinTouch).isEqualTo(48.dp)
        assertThat(MtDimens.PrimaryButtonHeight).isEqualTo(58.dp)
        assertThat(MtDimens.ScreenPadding).isEqualTo(24.dp)
        assertThat(MtDimens.CardRadius).isEqualTo(30.dp)
        assertThat(MtDimens.Vinyl).isEqualTo(286.dp)
    }

    @Test
    fun `text on the primary accent keeps 4_5 to 1 contrast in both themes (SRS Android 11)`() {
        listOf(false, true).forEach { dark ->
            val scheme = musicTimelyColorScheme(dark)
            assertThat(contrast(scheme.onPrimary, scheme.primary)).isAtLeast(4.5)
            assertThat(contrast(scheme.onBackground, scheme.background)).isAtLeast(4.5)
            assertThat(contrast(scheme.onSurfaceVariant, scheme.surfaceVariant)).isAtLeast(4.5)
        }
    }


    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun `spacing, radius and component sizes follow the design table (docs design README)`() {
        assertThat(MtSpacing.scale).containsExactly(4.dp, 8.dp, 12.dp, 16.dp, 20.dp, 24.dp, 32.dp, 48.dp).inOrder()
        assertThat(listOf(MtRadius.Small, MtRadius.Medium, MtRadius.Panel, MtRadius.Card, MtRadius.Pill))
            .containsExactly(16.dp, 24.dp, 26.dp, 30.dp, 999.dp).inOrder()
        with(MtDimens) {
            assertThat(CardPadding).isEqualTo(20.dp)
            assertThat(ScreenPadding).isEqualTo(24.dp)
            assertThat(CircleControl).isEqualTo(48.dp)
            assertThat(TimeChip).isEqualTo(70.dp)
            assertThat(TimeChipGap).isEqualTo(21.dp)
            assertThat(SessionControlSmall).isEqualTo(60.dp)
            assertThat(SessionControlLarge).isEqualTo(84.dp)
            assertThat(FieldHeight).isEqualTo(58.dp)
            assertThat(FieldRadius).isEqualTo(29.dp)
            assertThat(TranslationCardHeight).isEqualTo(188.dp)
            assertThat(ProgressPanelHeight).isEqualTo(134.dp)
            assertThat(MusicAppRowHeight).isEqualTo(68.dp)
            assertThat(MusicAppRowRadius).isEqualTo(34.dp)
            assertThat(PrimaryButtonHeight).isEqualTo(58.dp)
            assertThat(PrimaryButtonRadius).isEqualTo(29.dp)
            assertThat(SecondaryButtonHeight).isEqualTo(46.dp)
            assertThat(SecondaryButtonRadius).isEqualTo(23.dp)
            assertThat(ProgressCell).isEqualTo(38.dp to 10.dp)
            assertThat(ProgressCellGap).isEqualTo(5.dp)
            assertThat(ProgressTrackHeight).isEqualTo(20.dp)
            assertThat(ProgressTrackRadius).isEqualTo(5.dp)
            assertThat(ContentWidth).isEqualTo(350.dp)
            assertThat(ButtonWidth).isEqualTo(342.dp)
        }
    }

    @Test
    fun `sunset gradient stops are orange 0, coral 48 percent, red 100 percent`() {
        assertThat(MtColors.SunsetStops.toList())
            .containsExactly(0f to MtColors.Orange, 0.48f to MtColors.Coral, 1f to MtColors.Red).inOrder()
    }
}
