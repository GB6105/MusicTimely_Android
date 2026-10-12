package com.gb6105.musictimely.ui.component

import androidx.annotation.StringRes
import com.gb6105.musictimely.R
import com.gb6105.musictimely.domain.model.audio.NoiseKind
import com.gb6105.musictimely.domain.model.source.Provider

@StringRes
fun NoiseKind.displayName(): Int = when (this) {
    NoiseKind.WHITE -> R.string.noise_white
    NoiseKind.PINK -> R.string.noise_pink
}

@StringRes
fun Provider.displayName(): Int = when (this) {
    Provider.YOUTUBE_MUSIC -> R.string.provider_youtube_music
    Provider.SPOTIFY -> R.string.provider_spotify
    Provider.MELON -> R.string.provider_melon
    Provider.GENIE -> R.string.provider_genie
    Provider.FLO -> R.string.provider_flo
    Provider.APPLE_MUSIC -> R.string.provider_apple_music
    Provider.OTHER -> R.string.provider_other
}
