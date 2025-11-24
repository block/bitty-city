package xyz.block.bittycity.innie.testing

import arrow.core.raise.result
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.DepositTransitionEvent
import xyz.block.bittycity.innie.store.DepositEventOperations
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory fake implementation of DepositEventOperations for testing.
 */
class FakeDepositEventOperations : DepositEventOperations {
  private val events = ConcurrentHashMap<Long, DepositTransitionEvent>()
  private val depositTokenToId = ConcurrentHashMap<DepositToken, Long>()
  private val eventIdGenerator = AtomicLong(0)
  private val depositIdGenerator = AtomicLong(0)

  override fun insertDepositEvent(
    depositToken: DepositToken,
    fromState: DepositState?,
    toState: DepositState,
    depositSnapshot: Deposit
  ): Result<DepositTransitionEvent> = result {
    val eventId = eventIdGenerator.incrementAndGet()
    val depositId = depositTokenToId.getOrPut(depositToken) { depositIdGenerator.incrementAndGet() }
    val now = Instant.now()
    val event = DepositTransitionEvent(
      id = eventId,
      createdAt = now,
      updatedAt = now,
      version = 1L,
      depositId = depositId,
      from = fromState,
      to = toState,
      isProcessed = false,
      depositSnapshot = depositSnapshot.toString()
    )
    events[eventId] = event
    event
  }

  override fun fetchUnprocessedEvents(batchSize: Int): Result<List<DepositTransitionEvent>> = result {
    events.values
      .filter { !it.isProcessed }
      .sortedBy { it.id }
      .take(batchSize)
  }

  override fun markEventAsProcessed(eventId: Long): Result<Unit> = result {
    val event = events[eventId]
      ?: raise(IllegalStateException("Event with id $eventId not found"))
    events[eventId] = event.copy(isProcessed = true)
  }

  override fun fetchPreviousEvent(
    currentEvent: DepositTransitionEvent
  ): Result<DepositTransitionEvent> = result {
    events.values
      .filter { it.depositId == currentEvent.depositId && it.id < currentEvent.id }
      .maxByOrNull { it.id }
      ?: raise(IllegalStateException("No previous event found for deposit ${currentEvent.depositId}"))
  }

  fun clear() {
    events.clear()
    depositTokenToId.clear()
    eventIdGenerator.set(0)
    depositIdGenerator.set(0)
  }
}
