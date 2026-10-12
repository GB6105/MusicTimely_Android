package com.gb6105.musictimely.domain.repository

import com.gb6105.musictimely.domain.model.memo.MemoAdd
import com.gb6105.musictimely.domain.model.memo.ThoughtMemo

/** Thought memos of the current session (FR-A069). Never sent to analytics. */
interface MemoRepository {
    suspend fun add(sessionId: String, text: String): MemoAdd
    fun memosFor(sessionId: String): List<ThoughtMemo>

    /** Result screen [계속 보관]: kept until the user deletes data. */
    suspend fun keep(sessionId: String)

    /** Result screen [지우기]. */
    suspend fun discard(sessionId: String)
    suspend fun clear()
}
