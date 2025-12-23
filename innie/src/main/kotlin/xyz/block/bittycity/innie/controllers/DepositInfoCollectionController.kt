package xyz.block.bittycity.innie.controllers

import app.cash.kfsm.StateMachine
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.block.bittycity.innie.client.MetricsClient
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositReversalFailureReason
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.store.DepositStore
import xyz.block.domainapi.util.HurdleGroup
import xyz.block.domainapi.util.InfoCollectionController

abstract class DepositInfoCollectionController(
  override val pendingCollectionState: DepositState,
  override val stateMachine: StateMachine<DepositToken, Deposit, DepositState>,
  val depositStore: DepositStore,
  val metricsClient: MetricsClient,
): DepositController(
  stateMachine,
  metricsClient,
  depositStore
), InfoCollectionController<DepositToken, DepositState, Deposit, RequirementId>,
  DepositStateHelpers {
  override val logger: KLogger = KotlinLogging.logger {}

  override val hurdleGroups = mapOf<String, HurdleGroup<RequirementId>>()

  override fun onCancel(value: Deposit): Result<Deposit> =
    value.failReversal(DepositReversalFailureReason.CUSTOMER_CANCELLED, metricsClient)

  override fun requiresSecureEndpoint(requirement: RequirementId): Boolean =
    requirement.requiresSecureEndpoint
}
