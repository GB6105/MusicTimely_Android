package com.gb6105.musictimely.receiver

import com.gb6105.musictimely.data.notification.ProgressDismissal
import com.gb6105.musictimely.domain.model.timer.TimerCommand
import com.gb6105.musictimely.domain.usecase.session.DispatchTimerCommandUseCase
import java.util.UUID

/** Maps an alarm or notification action to a timer command. The end alarm only re-evaluates the clock. */
class SessionActionHandler(
    private val dispatch: DispatchTimerCommandUseCase,
    private val dismissal: ProgressDismissal,
) {
    suspend fun handle(action: String?) {
        when (action) {
            SessionActionReceiver.ACTION_END_ALARM -> dispatch.tick()
            SessionActionReceiver.ACTION_PAUSE -> dispatch(TimerCommand.Pause(newId()))
            SessionActionReceiver.ACTION_RESUME -> dispatch(TimerCommand.Resume(newId()))
            SessionActionReceiver.ACTION_FINISH -> dispatch(TimerCommand.Finish(newId()))
            SessionActionReceiver.ACTION_PROGRESS_DISMISSED -> dismissal.markDismissed()
        }
    }

    private fun newId() = UUID.randomUUID().toString()
}
