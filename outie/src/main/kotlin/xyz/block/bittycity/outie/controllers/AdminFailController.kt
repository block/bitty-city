package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import jakarta.inject.Inject
import xyz.block.bittycity.outie.fsm.WithdrawalEffect
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Operation

/**
 * A controller used to fail any stuck withdrawals.
 */
class AdminFailController @Inject constructor(
  stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>,
  withdrawalStore: WithdrawalStore
) : WithdrawalController(withdrawalStore, stateMachine) {

  override fun processInputs(
    value: Withdrawal,
    inputs: List<Input<RequirementId>>,
    operation: Operation,
    hurdleGroupId: String?
  ): Result<ProcessingState<Withdrawal, RequirementId>> = result {
    val updatedValue = failWithdrawal(FailureReason.ADMIN_FAILED, value).bind()
    ProcessingState.Complete(updatedValue)
  }

  override fun handleFailure(failure: Throwable, value: Withdrawal): Result<Withdrawal> = result {
    failWithdrawal(failure.toFailureReason(), value).bind()
  }
}
