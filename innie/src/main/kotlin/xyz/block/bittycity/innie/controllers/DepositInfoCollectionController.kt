package xyz.block.bittycity.innie.controllers

import app.cash.kfsm.v2.AwaitableStateMachine
import app.cash.kfsm.v2.StateMachine
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.block.bittycity.innie.fsm.DepositEffect
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositReversalFailureReason
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.store.DepositStore
import xyz.block.domainapi.kfsm.v2.util.HurdleGroup
import xyz.block.domainapi.kfsm.v2.util.InfoCollectionController

abstract class DepositInfoCollectionController(
  override val pendingCollectionState: DepositState,
  override val stateMachine: StateMachine<DepositToken, Deposit, DepositState, DepositEffect>,
  override val awaitableStateMachine: AwaitableStateMachine<DepositToken, Deposit, DepositState, DepositEffect>,
  val depositStore: DepositStore,
) : DepositController(stateMachine, awaitableStateMachine, depositStore),
  InfoCollectionController<DepositToken, DepositState, Deposit, RequirementId>,
  DepositStateHelpers {
  override val logger: KLogger = KotlinLogging.logger {}

  override val hurdleGroups = mapOf<String, HurdleGroup<RequirementId>>()

  override fun onCancel(value: Deposit): Result<Deposit> =
    value.failReversal(DepositReversalFailureReason.CUSTOMER_CANCELLED)

  override fun requiresSecureEndpoint(requirement: RequirementId): Boolean =
    requirement.requiresSecureEndpoint
}
