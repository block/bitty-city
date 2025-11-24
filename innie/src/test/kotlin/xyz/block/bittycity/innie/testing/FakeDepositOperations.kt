package xyz.block.bittycity.innie.testing

import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositReversal
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.DepositTransitionEvent
import xyz.block.bittycity.innie.store.DepositOperations

/**
 * In-memory fake implementation of DepositOperations that combines
 * FakeDepositEntityOperations and FakeDepositEventOperations.
 */
class FakeDepositOperations(
  private val entityOps: FakeDepositEntityOperations = FakeDepositEntityOperations(),
  private val eventOps: FakeDepositEventOperations = FakeDepositEventOperations()
) : DepositOperations {

  // DepositEntityOperations methods
  override fun insert(deposit: Deposit): Result<Deposit> =
    entityOps.insert(deposit)

  override fun getByToken(token: DepositToken): Result<Deposit> =
    entityOps.getByToken(token)

  override fun findByToken(token: DepositToken): Result<Deposit?> =
    entityOps.findByToken(token)

  override fun update(deposit: Deposit): Result<Deposit> =
    entityOps.update(deposit)

  override fun addReversal(id: DepositToken, reversal: DepositReversal): Result<DepositReversal> =
    entityOps.addReversal(id, reversal)

  override fun getLatestReversal(id: DepositToken): Result<DepositReversal?> =
    entityOps.getLatestReversal(id)

  // DepositEventOperations methods
  override fun insertDepositEvent(
    depositToken: DepositToken,
    fromState: DepositState?,
    toState: DepositState,
    depositSnapshot: Deposit
  ): Result<DepositTransitionEvent> =
    eventOps.insertDepositEvent(depositToken, fromState, toState, depositSnapshot)

  override fun fetchUnprocessedEvents(batchSize: Int): Result<List<DepositTransitionEvent>> =
    eventOps.fetchUnprocessedEvents(batchSize)

  override fun markEventAsProcessed(eventId: Long): Result<Unit> =
    eventOps.markEventAsProcessed(eventId)

  override fun fetchPreviousEvent(currentEvent: DepositTransitionEvent): Result<DepositTransitionEvent> =
    eventOps.fetchPreviousEvent(currentEvent)

  fun clear() {
    entityOps.clear()
    eventOps.clear()
  }
}
