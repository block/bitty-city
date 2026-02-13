package xyz.block.bittycity.innie.controllers

import app.cash.kfsm.v2.AwaitableStateMachine
import app.cash.kfsm.v2.StateMachine
import arrow.core.raise.result
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.block.bittycity.innie.fsm.DepositEffect
import xyz.block.bittycity.innie.fsm.DepositFailed
import xyz.block.bittycity.innie.fsm.ReversalFailed
import xyz.block.bittycity.innie.models.PendingReversal
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositFailureReason
import xyz.block.bittycity.innie.models.DepositReversalFailureReason
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.store.DepositStore
import xyz.block.domainapi.Input
import xyz.block.domainapi.kfsm.v2.util.Controller

abstract class DepositController(
  override val stateMachine: StateMachine<DepositToken, Deposit, DepositState, DepositEffect>,
  override val awaitableStateMachine: AwaitableStateMachine<DepositToken, Deposit, DepositState, DepositEffect>,
  private val depositStore: DepositStore,
) : Controller<DepositToken, DepositState, Deposit, RequirementId>, DepositStateHelpers {
  override val logger: KLogger = KotlinLogging.logger {}

  protected fun failDeposit(failure: Throwable, value: Deposit): Result<Deposit> = result {
    val valueFromDb = depositStore.getDepositByToken(value.id).bind()
    if (valueFromDb.state != PendingReversal) {
      logger.info(failure) { "Failing deposit ${valueFromDb.id}" }
      valueFromDb.fail(failure.toFailureReason()).bind()
    } else {
      raise(failure)
    }
  }

  protected fun failReversal(failure: Throwable, value: Deposit): Result<Deposit> =
    failReversal(failure.toReversalFailureReason(),  value)

  protected fun failReversal(failureReason: DepositReversalFailureReason, value: Deposit): Result<Deposit> = result {
    val valueFromDb = depositStore.getDepositByToken(value.id).bind()
    if (valueFromDb.state != PendingReversal) {
      logger.info { "Failing deposit ${valueFromDb.id}" }
      valueFromDb.failReversal(failureReason).bind()
    } else {
      logger.info { "No deposit reversal to fail for deposit: ${valueFromDb.id}" }
      valueFromDb
    }
  }
}

interface DepositStateHelpers {
  val logger: KLogger
  val stateMachine: StateMachine<DepositToken, Deposit, DepositState, DepositEffect>
  val awaitableStateMachine: AwaitableStateMachine<DepositToken, Deposit, DepositState, DepositEffect>

  fun Deposit.fail(reason: DepositFailureReason): Result<Deposit> = result {
    stateMachine.transition(
      value = copy(),
      transition = DepositFailed(reason)
    ).bind()
  }

  fun Deposit.failReversal(reason: DepositReversalFailureReason): Result<Deposit> = result {
    stateMachine.transition(
      value = updateCurrentReversal { it.copy(failureReason = reason) }.bind(),
      transition = ReversalFailed(reason)
    ).bind()
  }

  fun mismatchedHurdle(response: Input.HurdleResponse<RequirementId>) = IllegalArgumentException(
    "No update function for requirement ID ${response.id}"
  )

  fun mismatchedState(value: Deposit) = IllegalStateException(
    "Instance is ${value.state}, which is not a valid state for this controller"
  )
}
