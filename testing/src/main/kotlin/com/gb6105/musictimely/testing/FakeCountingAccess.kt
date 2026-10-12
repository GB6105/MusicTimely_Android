package com.gb6105.musictimely.testing

import com.gb6105.musictimely.domain.repository.CountingAccessPort

class FakeCountingAccess(var granted: Boolean = false) : CountingAccessPort {
    var settingsOpened = 0
        private set

    override fun isGranted() = granted

    override fun openAccessSettings() {
        settingsOpened++
    }
}
