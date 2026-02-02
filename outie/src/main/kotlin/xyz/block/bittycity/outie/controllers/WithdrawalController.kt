package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.block.bittycity.outie.fsm.Fail
import xyz.block.bittycity.outie.fsm.WithdrawalEffect
import xyz.block.bittycity.outie.models.Failed
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import xyz.block.domainapi.Input
import xyz.block.domainapi.util.Controller

abstract class WithdrawalController(
  private val withdrawalStore: WithdrawalStore,
  protected val stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>,
) : Controller<WithdrawalToken, WithdrawalState, Withdrawal, RequirementId> {
  val logger: KLogger = KotlinLogging.logger {}

  protected fun failWithdrawal(failureReason: FailureReason, value: Withdrawal): Result<Withdrawal> = result {
    val valueFromDb = withdrawalStore.getWithdrawalByToken(value.id).bind()
    if (valueFromDb.state !is Failed) {
      logger.info { "Failing withdrawal ${valueFromDb.id}" }
      stateMachine.transition(valueFromDb, Fail(failureReason)).bind()
    } else {
      logger.warn { "Withdrawal ${valueFromDb.id} was already failed" }
      valueFromDb
    }
  }

  protected fun mismatchedState(value: Withdrawal) = IllegalStateException(
    "Instance is ${value.state}, which is not a valid state for this controller"
  )

  protected fun mismatchedHurdle(response: Input.HurdleResponse<RequirementId>) = IllegalArgumentException(
    "No update function for requirement ID ${response.id}"
  )
}
