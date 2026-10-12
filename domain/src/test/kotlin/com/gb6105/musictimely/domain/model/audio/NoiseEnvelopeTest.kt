package com.gb6105.musictimely.domain.model.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 페이드·덕킹 (FR-A057, SRS v1 FR-029): 시작 1.5초 페이드 인, 일시정지 30%, 종료·도달 3초 페이드 아웃. 기본 볼륨 20%. */
class NoiseEnvelopeTest {

    private val volume = 0.2f

    @Test
    fun `default volume is 20 percent`() {
        assertThat(NoiseEnvelope.DEFAULT_VOLUME).isEqualTo(0.2f)
    }

    @Test
    fun `starting fades in over 1_5 seconds to the set volume`() {
        val env = NoiseEnvelope.start(volume, atMs = 0)

        assertThat(env.gainAt(0)).isEqualTo(0f)
        assertThat(env.gainAt(750)).isWithin(1e-4f).of(0.1f)
        assertThat(env.gainAt(1_500)).isWithin(1e-4f).of(0.2f)
        assertThat(env.gainAt(9_000)).isWithin(1e-4f).of(0.2f)
    }

    @Test
    fun `pausing the timer ducks to 30 percent of the set volume, resuming restores it`() {
        val ducked = NoiseEnvelope.start(volume, 0).duck(atMs = 5_000)

        assertThat(ducked.gainAt(5_000 + NoiseEnvelope.DUCK_MS)).isWithin(1e-4f).of(0.06f)
        assertThat(ducked.restore(atMs = 9_000).gainAt(9_000 + NoiseEnvelope.DUCK_MS)).isWithin(1e-4f).of(0.2f)
    }

    @Test
    fun `finishing or reaching the limit fades out over 3 seconds and then is silent`() {
        val ending = NoiseEnvelope.start(volume, 0).fadeOut(atMs = 10_000)

        assertThat(ending.gainAt(10_000)).isWithin(1e-4f).of(0.2f)
        assertThat(ending.gainAt(11_500)).isWithin(1e-4f).of(0.1f)
        assertThat(ending.gainAt(13_000)).isEqualTo(0f)
        assertThat(ending.isSilentAt(13_000)).isTrue()
        assertThat(ending.isSilentAt(12_999)).isFalse()
    }

    @Test
    fun `volume is clamped to 0 to 1`() {
        assertThat(NoiseEnvelope.start(1.7f, 0).gainAt(5_000)).isEqualTo(1f)
        assertThat(NoiseEnvelope.start(-1f, 0).gainAt(5_000)).isEqualTo(0f)
    }

    @Test
    fun `fade lengths follow the settings (FR-A070)`() {
        assertThat(NoiseEnvelope.start(0.5f, 0, fadeInMs = 0).gainAt(0)).isEqualTo(0.5f)
        assertThat(NoiseEnvelope.start(0.5f, 0, fadeInMs = 5_000).gainAt(2_500)).isWithin(1e-4f).of(0.25f)

        val ending = NoiseEnvelope.start(0.5f, 0, fadeInMs = 0, fadeOutMs = 10_000).fadeOut(atMs = 1_000)
        assertThat(ending.gainAt(6_000)).isWithin(1e-4f).of(0.25f)
        assertThat(ending.isSilentAt(10_999)).isFalse()
        assertThat(ending.isSilentAt(11_000)).isTrue()
    }

    @Test
    fun `sound setting ranges are volume 0 to 1, fade-in 0 to 5 s, fade-out 0 to 10 s (FR-A070)`() {
        assertThat(NoiseSound.isValidVolume(0f)).isTrue()
        assertThat(NoiseSound.isValidVolume(1f)).isTrue()
        assertThat(NoiseSound.isValidVolume(1.01f)).isFalse()
        assertThat(NoiseSound.isValidVolume(-0.01f)).isFalse()
        assertThat(NoiseSound.isValidFadeIn(0)).isTrue()
        assertThat(NoiseSound.isValidFadeIn(5_000)).isTrue()
        assertThat(NoiseSound.isValidFadeIn(5_001)).isFalse()
        assertThat(NoiseSound.isValidFadeOut(10_000)).isTrue()
        assertThat(NoiseSound.isValidFadeOut(10_001)).isFalse()
        assertThat(NoiseSound.isValidFadeOut(-1)).isFalse()
        assertThat(NoiseSound()).isEqualTo(NoiseSound(0.2f, 1_500, 3_000))
    }
}
