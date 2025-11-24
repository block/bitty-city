package xyz.block.bittycity.innie.store

import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.DepositTransitionEvent

interface DepositEventOperations {
  fun insertDepositEvent(
    depositToken: DepositToken,
    fromState: DepositState?,
    toState: DepositState,
    depositSnapshot: Deposit
  ): Result<DepositTransitionEvent>

  fun fetchUnprocessedEvents(batchSize: Int): Result<List<DepositTransitionEvent>>

  fun markEventAsProcessed(eventId: Long): Result<Unit>

  fun fetchPreviousEvent(
    currentEvent: DepositTransitionEvent
  ): Result<DepositTransitionEvent>
}
