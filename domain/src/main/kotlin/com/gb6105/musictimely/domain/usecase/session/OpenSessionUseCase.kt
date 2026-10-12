package com.gb6105.musictimely.domain.usecase.session

import com.gb6105.musictimely.domain.model.allocation.TaskTitle
import com.gb6105.musictimely.domain.model.timer.Allocation
import com.gb6105.musictimely.domain.model.timer.TimerSession
import com.gb6105.musictimely.domain.model.timer.TimerState
import com.gb6105.musictimely.domain.repository.SessionRepository

/**
 * FR-A013: one live session at a time. A new entry returns the live session instead of replacing it;
 * a READY session that never started takes the newly chosen allocation.
 */
class OpenSessionUseCase(
    private val repository: SessionRepository,
    private val newSessionId: () -> String,
) {
    suspend operator fun invoke(allocation: Allocation, taskTitle: TaskTitle? = null): TimerSession = repository.withLock {
        val active = repository.active()
        if (active != null && active.state != TimerState.ENDED && active.state != TimerState.READY) return@withLock active
        val id = active?.takeIf { it.state == TimerState.READY }?.sessionId ?: newSessionId()
        TimerSession.ready(id, allocation, taskTitle?.value).also { repository.save(it) }
    }
}
