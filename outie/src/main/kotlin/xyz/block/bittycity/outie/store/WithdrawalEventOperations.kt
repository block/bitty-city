package xyz.block.bittycity.outie.store

import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.models.WithdrawalTransitionEvent

interface WithdrawalEventOperations {
  fun insertWithdrawalEvent(
    withdrawalToken: WithdrawalToken,
    fromState: WithdrawalState?,
    toState: WithdrawalState,
    withdrawalSnapshot: Withdrawal,
  ): Result<WithdrawalTransitionEvent>

  fun fetchUnprocessedEvents(batchSize: Int): Result<List<WithdrawalTransitionEvent>>

  fun markEventAsProcessed(eventId: Long): Result<Unit>

  fun fetchPreviousEvent(
    currentEvent: WithdrawalTransitionEvent
  ): Result<WithdrawalTransitionEvent?>
}
