package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.DeferrableEffect
import app.cash.kfsm.OutboxHandler
import app.cash.kfsm.OutboxMessage
import app.cash.kfsm.Transition
import app.cash.kfsm.Transitioner
import app.cash.kfsm.annotations.ExperimentalLibraryApi
import arrow.core.raise.result
import xyz.block.bittycity.common.client.PreFlightClient
import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.outie.store.WithdrawalOperations
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject
import jakarta.inject.Singleton
import xyz.block.bittycity.common.utils.retry
import java.util.ArrayList
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.supplyAsync

/**
 * A transitioner for handling withdrawal state transitions.
 */
@Singleton
class WithdrawalTransitioner @Inject constructor(
  private val withdrawalTransactor: Transactor<WithdrawalOperations>,
  private val preflightClient: PreFlightClient<Withdrawal>,
  private val metricsClient: MetricsClient,
) : Transitioner<
  WithdrawalToken,
  Transition<WithdrawalToken, Withdrawal, WithdrawalState>,
  Withdrawal,
  WithdrawalState
  >() {
  private val logger: KLogger = KotlinLogging.logger {}

  @ExperimentalLibraryApi
  override val outboxHandler = BufferedOutboxHandler()

  override fun persist(
    from: WithdrawalState,
    value: Withdrawal,
    via: Transition<WithdrawalToken, Withdrawal, WithdrawalState>
  ): Result<Withdrawal> = result {
    withdrawalTransactor.transact("Persist state transition to ${via.to}") {
      update(value.copy(state = via.to)).onSuccess { updatedValue ->
        insertWithdrawalEvent(
          withdrawalToken = value.id,
          fromState = from,
          toState = via.to,
          withdrawalSnapshot = updatedValue,
        ).bind()
      }
    }.bind()
  }

  @OptIn(ExperimentalLibraryApi::class)
  override fun persistWithOutbox(
    from: WithdrawalState,
    value: Withdrawal,
    via: Transition<WithdrawalToken, Withdrawal, WithdrawalState>,
    outboxMessages: List<OutboxMessage<WithdrawalToken>>
  ): Result<Withdrawal> = result {
    withdrawalTransactor.transact<Withdrawal>("Persist state transition to ${via.to} with outbox") {
      val updatedValue = update(value.copy(state = via.to)).bind()

      insertWithdrawalEvent(
        withdrawalToken = value.id,
        fromState = from,
        toState = via.to,
        withdrawalSnapshot = updatedValue,
      ).bind()

      outboxMessages.forEach { message ->
        insertOutboxMessage(
          OutboxMessage(
            id = message.id,
            valueId = message.valueId.toString(),
            effectPayload = message.effectPayload,
            createdAt = message.createdAt,
            processedAt = message.processedAt,
            status = message.status,
            attemptCount = message.attemptCount,
            lastError = message.lastError
          )
        ).bind()
      }
      Result.success(updatedValue)
    }.bind()
  }

  override fun postHook(
    from: WithdrawalState,
    value: Withdrawal,
    via: Transition<WithdrawalToken, Withdrawal, WithdrawalState>
  ): Result<Unit> = result {
    listOf<CompletableFuture<Result<Unit>>>(
      supplyAsync {
        retry {
          preflightClient.doPreFlight(value)
            .recover {
              logger.warn(it) { "Preflight call for withdrawal ${value.id} failed" }
            }
        }
      },
      supplyAsync {
        retry {
          metricsClient.stateTransition(from, via.to, value.failureReason)
            .recover { logger.warn(it) { "Failure to publish state transition metrics" } }
        }
        retry {
          metricsClient.withdrawalSuccessAmount(value)
            .recover { logger.warn(it) { "Failure to publish success amount metrics" } }
        }
      }
    ).map { it.join().bind() }
  }

  @OptIn(ExperimentalLibraryApi::class)
  class BufferedOutboxHandler : OutboxHandler<WithdrawalToken, Withdrawal, WithdrawalState> {
    private val pendingMessages: MutableList<OutboxMessage<WithdrawalToken>> = 
        Collections.synchronizedList(ArrayList<OutboxMessage<WithdrawalToken>>())

    override fun captureEffect(
      value: Withdrawal,
      effect: DeferrableEffect<WithdrawalToken, Withdrawal, WithdrawalState>
    ): Result<Withdrawal> = result {
      val payload = effect.serialize(value).bind()
      val message = OutboxMessage(
        id = UUID.randomUUID().toString(),
        valueId = value.id,
        effectPayload = payload,
        createdAt = System.currentTimeMillis()
      )
      pendingMessages.add(message)
      value
    }

    override fun getPendingMessages(): List<OutboxMessage<WithdrawalToken>> = pendingMessages.toList()

    override fun clearPending() {
      pendingMessages.clear()
    }
  }
}
