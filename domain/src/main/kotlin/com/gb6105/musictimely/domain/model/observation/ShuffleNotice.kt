package com.gb6105.musictimely.domain.model.observation

/**
 * FR-A022 (SRS v1 FR-022): the shuffle notice appears only when shuffle is actually known to be on, once per
 * session. Unknown (D0, or a platform session that does not expose it) shows nothing. The app never turns shuffle off.
 */
data class ShuffleNotice(val shown: Boolean = false) {
    data class Result(val next: ShuffleNotice, val show: Boolean)

    fun on(shuffle: Boolean?): Result =
        if (shuffle == true && !shown) Result(ShuffleNotice(shown = true), show = true) else Result(this, show = false)
}
