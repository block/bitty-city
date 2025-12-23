package xyz.block.bittycity.innie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.block.bittycity.common.utils.retry
import xyz.block.bittycity.innie.client.MetricsClient
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositFailureReason
import xyz.block.bittycity.innie.models.DepositReversalFailureReason
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.models.WaitingForReversal
import xyz.block.bittycity.innie.store.DepositStore
import xyz.block.domainapi.Input
import xyz.block.domainapi.util.Controller
import java.util.concurrent.CompletableFuture.supplyAsync

abstract class DepositController(
  override val stateMachine: StateMachine<DepositToken, Deposit, DepositState>,
  private val metricsClient: MetricsClient,
  private val depositStore: DepositStore,
  ) : Controller<DepositToken, DepositState, Deposit, RequirementId>, DepositStateHelpers {
  override val logger: KLogger = KotlinLogging.logger {}

  protected fun failDeposit(failure: Throwable, value: Deposit): Result<Deposit> = result {
    val valueFromDb = depositStore.getDepositByToken(value.id).bind()
    if (valueFromDb.state != WaitingForReversal) {
      logger.info(failure) { "Failing deposit ${valueFromDb.id}" }
      valueFromDb.fail(failure.toFailureReason(), metricsClient).bind()
    } else {
      raise(failure)
    }
  }

  protected fun failReversal(failure: Throwable, value: Deposit): Result<Deposit> = result {
    val valueFromDb = depositStore.getDepositByToken(value.id).bind()
    if (valueFromDb.state != WaitingForReversal) {
      logger.info(failure) { "Failing deposit ${valueFromDb.id}" }
      valueFromDb.failReversal(failure.toReversalFailureReason(), metricsClient).bind()
    } else {
      raise(failure)
    }
  }

}

interface DepositStateHelpers {
  val logger: KLogger
  val stateMachine: StateMachine<DepositToken, Deposit, DepositState>

  fun Deposit.transitionTo(
    state: DepositState,
    metricsClient: MetricsClient,
  ): Result<Deposit> = result {
    stateMachine.transitionTo(this@transitionTo, state).bind()
  }

  fun Deposit.fail(reason: DepositFailureReason, metricsClient: MetricsClient): Result<Deposit> = result {
    stateMachine.transitionTo(
      value = copy(failureReason = reason),
      targetState = WaitingForReversal
    ).onSuccess {
      supplyAsync {
        retry {
          metricsClient.failureReason(reason)
            .recover { logger.warn(it) { "Failure to publish metrics" } }
        }
      }
    }.bind()
  }

  fun Deposit.failReversal(reason: DepositReversalFailureReason, metricsClient: MetricsClient): Result<Deposit> = result {
    stateMachine.transitionTo(
      value = updateCurrentReversal { it.copy(failureReason = reason) }.bind(),
      targetState = WaitingForReversal
    ).onSuccess {
      supplyAsync {
        retry {
          metricsClient.reversalFailureReason(reason)
            .recover { logger.warn(it) { "Failure to publish metrics" } }
        }
      }
    }.bind()
  }

  fun mismatchedHurdle(response: Input.HurdleResponse<RequirementId>) = IllegalArgumentException(
    "No update function for requirement ID ${response.id}"
  )

  fun mismatchedState(value: Deposit) = IllegalStateException(
    "Instance is ${value.state}, which is not a valid state for this controller"
  )
}
