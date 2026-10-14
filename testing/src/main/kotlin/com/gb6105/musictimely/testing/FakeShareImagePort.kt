package com.gb6105.musictimely.testing

import com.gb6105.musictimely.domain.model.shareimage.ShareCard
import com.gb6105.musictimely.domain.repository.ShareImagePort
import com.gb6105.musictimely.domain.repository.ShareTarget

class FakeShareImagePort(
    var image: ByteArray? = byteArrayOf(1),
    var target: ShareTarget? = ShareTarget.INSTAGRAM,
) : ShareImagePort {
    val prepared = mutableListOf<ShareCard>()
    var sent = 0
    var cleared = 0

    override suspend fun prepare(card: ShareCard): ByteArray? {
        prepared += card
        return image
    }

    override fun send(): ShareTarget? {
        sent++
        return target
    }

    override suspend fun clear() {
        cleared++
    }
}
