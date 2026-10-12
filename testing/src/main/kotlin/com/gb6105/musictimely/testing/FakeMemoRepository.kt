package com.gb6105.musictimely.testing

import com.gb6105.musictimely.domain.model.memo.MemoAdd
import com.gb6105.musictimely.domain.model.memo.ThoughtMemo
import com.gb6105.musictimely.domain.model.memo.ThoughtMemos
import com.gb6105.musictimely.domain.repository.MemoRepository

class FakeMemoRepository : MemoRepository {
    private val bySession = mutableMapOf<String, ThoughtMemos>()
    var failNext = false

    override suspend fun add(sessionId: String, text: String): MemoAdd {
        if (failNext) {
            failNext = false
            throw java.io.IOException("disk full")
        }
        val current = bySession[sessionId] ?: ThoughtMemos.empty(sessionId)
        val result = current.add("m${current.items.size}", text, 0)
        if (result is MemoAdd.Added) bySession[sessionId] = result.memos
        return result
    }

    override fun memosFor(sessionId: String): List<ThoughtMemo> = bySession[sessionId]?.items.orEmpty()

    val keptSessions = mutableSetOf<String>()

    override suspend fun keep(sessionId: String) {
        keptSessions += sessionId
    }

    override suspend fun discard(sessionId: String) {
        bySession.remove(sessionId)
    }

    override suspend fun clear() = bySession.clear()
}
