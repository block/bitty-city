package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.StateMachine
import xyz.block.bittycity.outie.fsm.Fail
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import xyz.block.bittycity.outie.fsm.WithdrawalEffect
import xyz.block.domainapi.util.HurdleGroup
import xyz.block.domainapi.util.InfoCollectionController

abstract class WithdrawalInfoCollectionController(
  override val pendingCollectionState: WithdrawalState,
  val stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>,
  val withdrawalStore: WithdrawalStore
) : InfoCollectionController<WithdrawalToken, WithdrawalState, Withdrawal, RequirementId> {

  override val hurdleGroups = mapOf<String, HurdleGroup<RequirementId>>()

  override fun onCancel(value: Withdrawal): Result<Withdrawal> =
    stateMachine.transition(value, Fail(FailureReason.CUSTOMER_CANCELLED))

  override fun requiresSecureEndpoint(requirement: RequirementId): Boolean =
    requirement.requiresSecureEndpoint
}
