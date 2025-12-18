package xyz.block.bittycity.innie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import jakarta.inject.Inject
import xyz.block.bittycity.common.client.Eligibility
import xyz.block.bittycity.common.client.EligibilityClient
import xyz.block.bittycity.innie.client.MetricsClient
import xyz.block.bittycity.innie.models.CheckingDepositRisk
import xyz.block.bittycity.innie.models.CheckingEligibility
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositFailureReason.INELIGIBLE
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.store.DepositStore
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Operation

class EligibilityController  @Inject constructor(
  stateMachine: StateMachine<DepositToken, Deposit, DepositState>,
  depositStore: DepositStore,
  private val eligibilityClient: EligibilityClient,
  private val metricsClient: MetricsClient,
) : DepositController(stateMachine, metricsClient, depositStore) {

  override fun processInputs(
    value: Deposit,
    inputs: List<Input<RequirementId>>,
    operation: Operation,
    hurdleGroupId: String?
  ): Result<ProcessingState<Deposit, RequirementId>> = result {
    when (value.state) {
      CheckingEligibility -> {
        val eligibility = eligibilityClient.productEligibility(
          value.customerId.id
        ).bind()
        when (eligibility) {
          is Eligibility.Eligible -> value.transitionTo(CheckingDepositRisk, metricsClient)
          is Eligibility.Ineligible -> value.fail(INELIGIBLE, metricsClient)
        }.bind()
      }
      else -> raise(mismatchedState(value))
    }.asProcessingState()
  }

  override fun handleFailure(
    failure: Throwable,
    value: Deposit
  ): Result<Deposit> = result {
    logger.warn(failure) {
      "An unexpected error occurred checking the eligibility of an on-chain deposit. The deposit will not be failed."
    }
    raise(failure)
  }
}
