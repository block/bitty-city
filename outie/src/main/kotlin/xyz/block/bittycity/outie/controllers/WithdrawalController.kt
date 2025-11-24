package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.models.Failed
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import xyz.block.bittycity.common.utils.retry
import xyz.block.domainapi.Input
import xyz.block.domainapi.util.Controller
import java.util.concurrent.CompletableFuture.supplyAsync

abstract class WithdrawalController(
  override val stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState>,
  private val metricsClient: MetricsClient,
  private val withdrawalStore: WithdrawalStore,
) : Controller<WithdrawalToken, WithdrawalState, Withdrawal, RequirementId>,
  WithdrawalTransitioner {
  override val logger: KLogger = KotlinLogging.logger {}

  protected fun failWithdrawal(failure: Throwable, value: Withdrawal): Result<Withdrawal> = result {
    val valueFromDb = withdrawalStore.getWithdrawalByToken(value.id).bind()
    if (valueFromDb.state != Failed) {
      logger.warn(failure) { "Failing withdrawal ${valueFromDb.id}" }
      valueFromDb.fail(failure.toFailureReason(), metricsClient).bind()
    } else {
      raise(failure)
    }
  }
}

interface WithdrawalTransitioner {
  val logger: KLogger
  val stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState>

  fun Withdrawal.transitionTo(
    state: WithdrawalState,
    metricsClient: MetricsClient
  ): Result<Withdrawal> = result {
    stateMachine.transitionTo(this@transitionTo, state).bind()
  }

  fun Withdrawal.fail(reason: FailureReason, metricsClient: MetricsClient): Result<Withdrawal> =
    result {
      stateMachine.transitionTo(
        value = copy(failureReason = reason),
        targetState = Failed
      ).onSuccess {
        supplyAsync {
          retry {
            metricsClient.failureReason(reason)
              .recover { logger.warn(it) { "Failure to publish metrics" } }
          }
        }
      }.bind()
    }

  fun mismatchedHurdle(response: Input.HurdleResponse<RequirementId>) = IllegalArgumentException(
    "No update function for requirement ID ${response.id}"
  )

  fun mismatchedState(value: Withdrawal) = IllegalStateException(
    "Instance is ${value.state}, which is not a valid state for this controller"
  )
}
