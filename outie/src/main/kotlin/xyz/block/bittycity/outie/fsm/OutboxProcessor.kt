package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.OutboxMessage
import app.cash.kfsm.annotations.ExperimentalLibraryApi
import arrow.core.raise.result
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.store.WithdrawalOperations
import com.squareup.moshi.Moshi
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject

class OutboxProcessor @Inject constructor(
  private val transactor: Transactor<WithdrawalOperations>,
  private val fail: Fail,
  private val freezeFunds: FreezeFunds,
  private val submittedOnChain: SubmittedOnChain,
  moshi: Moshi
) {
  private val logger: KLogger = KotlinLogging.logger {}
  private val adapter = moshi.adapter(Withdrawal::class.java)

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
    val withdrawal = adapter.fromJson(message.effectPayload.data)
      ?: raise(IllegalStateException("Failed to deserialize withdrawal"))

    when (message.effectPayload.effectType) {
      fail.effectType -> fail.effect(withdrawal).bind()
      freezeFunds.effectType -> freezeFunds.effect(withdrawal).bind()
      submittedOnChain.effectType -> submittedOnChain.effect(withdrawal).bind()
      else -> logger.warn { "Unknown effect type: ${message.effectPayload.effectType}" }
    }

    transactor.transact("mark outbox processed") {
      markAsProcessed(message.id)
    }.bind()
  }

  companion object {
    private const val BATCH_SIZE = 20
  }
}
