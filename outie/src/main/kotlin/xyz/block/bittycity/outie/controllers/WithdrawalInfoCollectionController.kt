package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.StateMachine
import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.block.domainapi.util.HurdleGroup
import xyz.block.domainapi.util.InfoCollectionController

abstract class WithdrawalInfoCollectionController(
  override val pendingCollectionState: WithdrawalState,
  override val stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState>,
  val metricsClient: MetricsClient,
  val withdrawalStore: WithdrawalStore
) : WithdrawalController(
  stateMachine,
  metricsClient,
  withdrawalStore
),
  InfoCollectionController<WithdrawalToken, WithdrawalState, Withdrawal, RequirementId>,
  WithdrawalTransitioner {

  override val logger: KLogger = KotlinLogging.logger {}

  override val hurdleGroups = mapOf<String, HurdleGroup<RequirementId>>()

  override fun onCancel(value: Withdrawal): Result<Withdrawal> =
    value.fail(FailureReason.CUSTOMER_CANCELLED, metricsClient)

  override fun requiresSecureEndpoint(requirement: RequirementId): Boolean =
    requirement.requiresSecureEndpoint
}
