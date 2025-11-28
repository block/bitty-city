package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.OutboxMessage
import app.cash.kfsm.OutboxStatus
import app.cash.kfsm.DeferrableEffect
import app.cash.kfsm.annotations.ExperimentalLibraryApi
import arrow.core.raise.result
import com.google.inject.Injector
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.store.WithdrawalOperations
import com.squareup.moshi.Moshi
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject

class OutboxProcessor @Inject constructor(
  private val transactor: Transactor<WithdrawalOperations>,
  private val injector: Injector,
  moshi: Moshi
) {
  private val logger: KLogger = KotlinLogging.logger {}
  private val adapter = moshi.adapter(Withdrawal::class.java)

  @OptIn(ExperimentalLibraryApi::class)
  private val deferredTransitions: Map<String, WithdrawalTransition> = WithdrawalTransition::class.sealedSubclasses
    .map { injector.getInstance(it.java) }
    .filter { it is DeferrableEffect<*, *, *> }
    .associateBy { (it as DeferrableEffect<*, *, *>).effectType }

  fun processBatches(): Result<Unit> = processBatchesRecursive()

  @OptIn(ExperimentalLibraryApi::class)
  private tailrec fun processBatchesRecursive(): Result<Unit> = result {
    val messages = transactor.transactReadOnly("fetch pending outbox") {
      fetchPendingMessages(BATCH_SIZE)
    }.bind()

    if (messages.isNotEmpty()) {
      messages.forEach { message ->
        result {
          processMessage(message).bind()
        }.onFailure {
          logger.error(it) { "Failed to process outbox message: ${message.id}" }
        }
      }
      if (messages.size == BATCH_SIZE) {
        return processBatchesRecursive()
      }
    }
  }

  @OptIn(ExperimentalLibraryApi::class)
  private fun processMessage(message: OutboxMessage<String>): Result<Unit> = result {
    val previousMessage = transactor.transactReadOnly("fetch prior outbox message") {
      fetchPreviousOutboxMessage(message)
    }.bind()

    if (previousMessage == null || previousMessage.status == OutboxStatus.COMPLETED) {
      val withdrawal = adapter.fromJson(message.effectPayload.data)
        ?: raise(IllegalStateException("Failed to deserialize withdrawal"))

      val transition = deferredTransitions[message.effectPayload.effectType]

      if (transition != null) {
        transition.effect(withdrawal).bind()
      } else {
        raise(IllegalStateException("Unknown effect type: ${message.effectPayload.effectType}"))
      }

      transactor.transact("mark outbox processed") {
        markAsProcessed(message.id)
      }.bind()
    } else {
      logger.warn {
        "Did not process message ${message.id} because the previous message " +
                "(${previousMessage.id}) was not processed (status=${previousMessage.status})"
      }
    }
  }

  companion object {
    private const val BATCH_SIZE = 20
  }
}
