package com.gb6105.musictimely.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

internal fun musicTimelyColorScheme(dark: Boolean) = with(if (dark) MtColors.Dark else MtColors.Light) {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    base.copy(
        primary = MtColors.Coral,
        // Ink on coral (≈5.8:1). White on the sunset accents is 2.1–3.5:1 — pending the designer's decision.
        onPrimary = MtColors.Light.ink,
        secondary = MtColors.Navy,
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = field,
        onSurfaceVariant = muted,
        outline = muted,
        error = MtColors.Red,
    )
}

private val typography = Typography(
    headlineSmall = MtType.songAmount,
    titleLarge = MtType.screenTitle,
    titleMedium = MtType.sectionTitle,
    titleSmall = MtType.button,
    bodyLarge = MtType.button,
    bodyMedium = MtType.body,
    bodySmall = MtType.bodySmall,
    labelLarge = MtType.button,
    labelSmall = MtType.caption,
)

/** Brand theme from the design tokens; Light/Dark follow the system (Navy pending, PRD open question 2). */
@Composable
fun MusicTimelyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = musicTimelyColorScheme(darkTheme), typography = typography, content = content)
}
