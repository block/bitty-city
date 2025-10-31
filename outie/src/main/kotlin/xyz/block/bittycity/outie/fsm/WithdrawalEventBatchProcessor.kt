package xyz.block.bittycity.outie.fsm

import arrow.core.raise.result
import xyz.block.bittycity.outie.store.Transactor
import xyz.block.bittycity.outie.store.WithdrawalOperations
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject

/**
 * Processes withdrawal events in batches, fetching unprocessed events and processing them
 * one at a time until no more events are available or an error occurs.
 */
class WithdrawalEventBatchProcessor @Inject constructor(
  private val withdrawalTransactor: Transactor<WithdrawalOperations>,
  private val eventProcessor: WithdrawalEventProcessor,
) {

  private val logger: KLogger = KotlinLogging.logger {}

  /**
   * Processes batches of unprocessed withdrawal events.
   *
   * Fetches batches of up to DEFAULT_BATCH_SIZE unprocessed events and processes each one
   * individually. After successful processing, marks each event as processed. If the processed
   * batch size equals the requested size, immediately fetches another batch. Returns when
   * the processed batch size is less than the requested size or an error occurs.
   *
   * @return Result indicating success or failure of the batch processing operation
   */
  fun processBatches(): Result<Unit> = processBatchesRecursive()

  private tailrec fun processBatchesRecursive(): Result<Unit> = result {
    val events = withdrawalTransactor.transactReadOnly("fetch withdrawal events") {
      fetchUnprocessedEvents(DEFAULT_BATCH_SIZE)
    }.bind()

    if (events.isNotEmpty()) {
      logger.info { "Fetched a batch of ${events.size} withdrawal events" }
      // Process each event individually
      events.forEach { event ->
        result {
          eventProcessor.processEvent(event).bind()
          withdrawalTransactor.transact("processed withdrawal event") {
            markEventAsProcessed(event.id)
          }.bind()
        }.onFailure {
          logger.error(it) { "Failed to process event: ${event.id}" }
        }
      }

      // Continue recursively if we processed a full batch
      if (events.size == DEFAULT_BATCH_SIZE) {
        return processBatchesRecursive()
      }
    }
  }

  companion object {
    private const val DEFAULT_BATCH_SIZE = 20
  }
}
