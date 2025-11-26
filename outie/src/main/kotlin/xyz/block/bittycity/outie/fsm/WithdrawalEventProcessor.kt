package xyz.block.bittycity.outie.fsm

import app.cash.quiver.extensions.catch
import app.cash.quiver.extensions.mapFailure
import arrow.core.raise.result
import com.squareup.moshi.Moshi
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.outie.client.EventClient
import xyz.block.bittycity.outie.client.WithdrawalEvent
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalTransitionEvent
import xyz.block.bittycity.outie.store.WithdrawalOperations

/**
 * Open class for processing withdrawal transition events.
 * The default implementation publishes events to EventClient.
 * Concrete implementations can override the processing logic.
 */
open class WithdrawalEventProcessor @Inject constructor(
  private val withdrawalTransactor: Transactor<WithdrawalOperations>,
  private val eventClient: EventClient,
  private val moshi: Moshi
) {
  private val logger: KLogger = KotlinLogging.logger {}

  /**
   * Process a withdrawal transition event.
   * Deserializes the withdrawal snapshot and publishes a WithdrawalEvent to the EventClient.
   * Then undertakes any additional processing specified by the bespoke implementation.
   *
   * @param event The event to process
   * @return Result indicating success or failure of processing
   */
  open fun processEvent(event: WithdrawalTransitionEvent): Result<Unit> = result {
    val withdrawal = event.withdrawalSnapshot?.let(::deserializeWithdrawal)?.onFailure {
      logger.error(it) { "Failure to parse withdrawal transition event ${event.id}" }
    }?.bind()

    logger.info { "Processing transition event ${event.id} for withdrawal ${withdrawal?.id}" }

    // Fetch the previous event for the same withdrawal ID
    val previousWithdrawalEvent = withdrawalTransactor.transactReadOnly("fetch prior event") {
      fetchPreviousEvent(event)
    }.bind()

    // Only process if previous event exists and has been successfully processed
    if ((previousWithdrawalEvent?.isProcessed) ?: true) {
      publishEvent(withdrawal, previousWithdrawalEvent).bind()
      additionalHandling(withdrawal, event).bind()
    } else {
      logger.warn {
        "Did not process event ${event.id} because the previous event " +
          "(${previousWithdrawalEvent.id}) was not processed"
      }
    }
  }

  open fun additionalHandling(
    withdrawal: Withdrawal?,
    event: WithdrawalTransitionEvent
  ): Result<Unit> = result { }

  private fun publishEvent(
    withdrawal: Withdrawal?,
    previousWithdrawalEvent: WithdrawalTransitionEvent?
  ) = result {
    when (withdrawal) {
      null -> Unit
      else -> {
        val previousWithdrawal = previousWithdrawalEvent?.withdrawalSnapshot
          ?.let(::deserializeWithdrawal)?.bind()

        val eventType =
          if (previousWithdrawalEvent == null) {
            WithdrawalEvent.EventType.CREATE
          } else {
            WithdrawalEvent.EventType.UPDATE
          }

        val withdrawalEvent = WithdrawalEvent(
          withdrawalToken = withdrawal.id,
          newWithdrawal = withdrawal,
          oldWithdrawal = previousWithdrawal,
          eventType = eventType
        )

        eventClient.publish(withdrawalEvent).bind()
      }
    }
  }

  private fun deserializeWithdrawal(jsonString: String): Result<Withdrawal> = result {
    Result.catch {
      val adapter = moshi.adapter(Withdrawal::class.java)
      adapter.fromJson(jsonString)
        ?: raise(IllegalStateException("Failed to deserialize withdrawal"))
    }.mapFailure {
      IllegalStateException("Failed to deserialize withdrawal: ${it.message}", it)
    }.bind()
  }
}
