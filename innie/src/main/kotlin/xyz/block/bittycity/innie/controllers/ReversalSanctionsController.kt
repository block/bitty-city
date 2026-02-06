package xyz.block.bittycity.innie.controllers

import app.cash.kfsm.v2.AwaitableStateMachine
import app.cash.kfsm.v2.StateMachine
import arrow.core.raise.result
import jakarta.inject.Inject
import xyz.block.bittycity.innie.fsm.DepositEffect
import xyz.block.bittycity.innie.fsm.ReversalFailed
import xyz.block.bittycity.innie.fsm.ReversalSanctionsApproved
import xyz.block.bittycity.innie.fsm.ReversalSanctionsDecisionFrozen
import xyz.block.bittycity.innie.models.CheckingReversalSanctions
import xyz.block.bittycity.innie.models.CollectingReversalSanctionsInfo
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositReversalFailureReason.SANCTIONS_FAILED
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.models.SanctionsHeldDecision
import xyz.block.bittycity.innie.models.SanctionsReviewDecision.APPROVE
import xyz.block.bittycity.innie.models.SanctionsReviewDecision.DECLINE
import xyz.block.bittycity.innie.models.SanctionsReviewDecision.FREEZE
import xyz.block.bittycity.innie.models.WaitingForReversalSanctionsHeldDecision
import xyz.block.bittycity.innie.store.DepositStore
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.kfsm.v2.util.Operation

class ReversalSanctionsController @Inject constructor(
  stateMachine: StateMachine<DepositToken, Deposit, DepositState, DepositEffect>,
  awaitableStateMachine: AwaitableStateMachine<DepositToken, Deposit, DepositState, DepositEffect>,
  depositStore: DepositStore
) : DepositController(stateMachine, awaitableStateMachine, depositStore) {
  override fun processInputs(
    value: Deposit,
    inputs: List<Input<RequirementId>>,
    operation: Operation,
    hurdleGroupId: String?
  ): Result<ProcessingState<Deposit, RequirementId>> = result {
    when (value.state) {
      CheckingReversalSanctions -> ProcessingState.Complete(value)
      WaitingForReversalSanctionsHeldDecision, CollectingReversalSanctionsInfo -> {
        val updatedValue = processInputs(value, inputs).bind()
        ProcessingState.Waiting(updatedValue)
      }
      else -> raise(mismatchedState(value))
    }
  }

  fun processInputs(
    value: Deposit,
    inputs: List<Input<RequirementId>>
  ): Result<Deposit> = result {
    if (inputs.all { it is Input.ResumeResult }) {
      inputs.filterIsInstance<SanctionsHeldDecision>().firstOrNull()?.let {
        when (it.decision) {
          APPROVE -> stateMachine.transition(value, ReversalSanctionsApproved())
          DECLINE -> stateMachine.transition(value, ReversalFailed(SANCTIONS_FAILED))
          FREEZE -> stateMachine.transition(value, ReversalSanctionsDecisionFrozen())
        }.bind()
      } ?: raise(IllegalArgumentException("No decision found for ${value.customerId}"))
    } else {
      raise(IllegalArgumentException("Inputs should be resume results"))
    }
  }

  override fun handleFailure(
    failure: Throwable,
    value: Deposit
  ): Result<Deposit> = result {
    raise(failure)
  }
}
