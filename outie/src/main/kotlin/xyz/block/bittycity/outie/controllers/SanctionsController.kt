package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import jakarta.inject.Inject
import xyz.block.bittycity.outie.fsm.Fail
import xyz.block.bittycity.outie.fsm.SanctionsHeldApprove
import xyz.block.bittycity.outie.fsm.SanctionsHeldSanction
import xyz.block.bittycity.outie.fsm.WithdrawalEffect
import xyz.block.bittycity.outie.models.CollectingSanctionsInfo
import xyz.block.bittycity.outie.models.FailureReason.SANCTIONS_DECLINED
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.SanctionsHeldDecision
import xyz.block.bittycity.outie.models.SanctionsReviewDecision
import xyz.block.bittycity.outie.models.WaitingForSanctionsHeldDecision
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Operation

class SanctionsController @Inject constructor(
  stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>,
  withdrawalStore: WithdrawalStore
) : WithdrawalController(withdrawalStore, stateMachine) {

  override fun processInputs(
    value: Withdrawal,
    inputs: List<Input<RequirementId>>,
    operation: Operation,
    hurdleGroupId: String?
  ): Result<ProcessingState<Withdrawal, RequirementId>> = result {
    when (value.state) {
      WaitingForSanctionsHeldDecision, CollectingSanctionsInfo -> {
        processInputs(value, inputs).bind()
        ProcessingState.Waiting(value)
      }
      else -> raise(mismatchedState(value))
    }
  }

  override fun handleFailure(failure: Throwable, value: Withdrawal): Result<Withdrawal> = result {
    when (value.state) {
      WaitingForSanctionsHeldDecision, CollectingSanctionsInfo -> {
        logger.error(failure) {
          "An unexpected error occurred while processing sanctions information. " +
            "The withdrawal will not be failed."
        }
        raise(failure)
      }

      else -> failWithdrawal(failure.toFailureReason(), value).bind()
    }
  }

  fun processInputs(
    value: Withdrawal,
    inputs: List<Input<RequirementId>>
  ): Result<Withdrawal> = result {
    if (inputs.all { it is Input.ResumeResult }) {
      inputs.filterIsInstance<SanctionsHeldDecision>().firstOrNull()?.let {
        when (it.decision) {
          SanctionsReviewDecision.APPROVE -> stateMachine.transition(value, SanctionsHeldApprove())
          SanctionsReviewDecision.DECLINE -> stateMachine.transition(value, Fail(SANCTIONS_DECLINED))
          SanctionsReviewDecision.FREEZE -> stateMachine.transition(value, SanctionsHeldSanction())
        }.bind()
      } ?: raise(IllegalArgumentException("No decision found for ${value.customerId}"))
    } else {
      raise(IllegalArgumentException("Inputs should be resume results"))
    }
  }
}
