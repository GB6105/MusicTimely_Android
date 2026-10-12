package com.gb6105.musictimely.di

import com.gb6105.musictimely.domain.repository.ClockPort
import com.gb6105.musictimely.domain.repository.NoisePort
import com.gb6105.musictimely.domain.repository.SessionRepository
import com.gb6105.musictimely.domain.usecase.session.DispatchTimerCommandUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug builds only: lets *DeviceTest reach the app's real singletons (alarm, receiver, sound service paths). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DeviceTestEntryPoint {
    fun sessions(): SessionRepository
    fun dispatch(): DispatchTimerCommandUseCase
    fun clock(): ClockPort
    fun noise(): NoisePort
}
