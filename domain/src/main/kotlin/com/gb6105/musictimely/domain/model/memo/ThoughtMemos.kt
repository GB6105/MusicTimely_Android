package com.gb6105.musictimely.domain.model.memo

data class ThoughtMemo(val id: String, val text: String, val createdAtEpochMs: Long, val kept: Boolean = false)

sealed interface MemoAdd {
    data class Added(val memos: ThoughtMemos) : MemoAdd
    data object Blank : MemoAdd
    data object TooLong : MemoAdd
    data object Full : MemoAdd
}

/** FR-A069 (SRS v1 FR-037, development default): 500 characters per memo, 20 memos per active session. */
data class ThoughtMemos(val sessionId: String, val items: List<ThoughtMemo>) {
    fun add(id: String, text: String, atEpochMs: Long): MemoAdd {
        val trimmed = text.trim()
        return when {
            trimmed.isEmpty() -> MemoAdd.Blank
            trimmed.length > MAX_CHARS -> MemoAdd.TooLong
            items.size >= MAX_ITEMS -> MemoAdd.Full
            else -> MemoAdd.Added(copy(items = items + ThoughtMemo(id, trimmed, atEpochMs)))
        }
    }

    companion object {
        const val MAX_CHARS = 500
        const val MAX_ITEMS = 20

        fun empty(sessionId: String) = ThoughtMemos(sessionId, emptyList())
    }
}

/** SRS v1 §9.3: memos nobody chose to keep are deleted 24 hours after they were written. */
object MemoRetention {
    const val UNHANDLED_TTL_MS: Long = 86_400_000

    fun alive(memos: List<ThoughtMemo>, nowEpochMs: Long): List<ThoughtMemo> =
        memos.filter { it.kept || nowEpochMs - it.createdAtEpochMs <= UNHANDLED_TTL_MS }
}
