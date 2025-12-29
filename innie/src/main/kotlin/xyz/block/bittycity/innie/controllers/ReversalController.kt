package xyz.block.bittycity.innie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import jakarta.inject.Inject
import xyz.block.bittycity.innie.client.MetricsClient
import xyz.block.bittycity.innie.models.CollectingInfo
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.models.WaitingForReversal
import xyz.block.bittycity.innie.store.DepositStore
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Operation

class ReversalController @Inject constructor(
  stateMachine: StateMachine<DepositToken, Deposit, DepositState>,
  depositStore: DepositStore,
  private val metricsClient: MetricsClient,
) : DepositController(stateMachine, metricsClient, depositStore) {
  override fun processInputs(
    value: Deposit,
    inputs: List<Input<RequirementId>>,
    operation: Operation,
    hurdleGroupId: String?
  ): Result<ProcessingState<Deposit, RequirementId>> = result {
    when (value.state) {
      WaitingForReversal -> value.transitionTo(CollectingInfo, metricsClient).bind()
      else -> raise(mismatchedState(value))
    }.asProcessingState()
  }

  override fun handleFailure(
    failure: Throwable,
    value: Deposit
  ): Result<Deposit> = result {
    logger.warn(failure) {
      "An unexpected error occurred starting a deposit reversal."
    }
    raise(failure)
  }
}
