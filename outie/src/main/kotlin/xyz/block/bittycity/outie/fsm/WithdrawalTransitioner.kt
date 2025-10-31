package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.Transition
import app.cash.kfsm.Transitioner
import arrow.core.raise.result
import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.client.PreFlightClient
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.Transactor
import xyz.block.bittycity.outie.store.WithdrawalOperations
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject
import jakarta.inject.Singleton
import xyz.block.bittycity.outie.utils.retry
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.supplyAsync

/**
 * A transitioner for handling withdrawal state transitions.
 */
@Singleton
class WithdrawalTransitioner @Inject constructor(
  private val withdrawalTransactor: Transactor<WithdrawalOperations>,
  private val preflightClient: PreFlightClient,
  private val metricsClient: MetricsClient,
) : Transitioner<
  WithdrawalToken,
  Transition<WithdrawalToken, Withdrawal, WithdrawalState>,
  Withdrawal,
  WithdrawalState
  >() {
  private val logger: KLogger = KotlinLogging.logger {}

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
}
