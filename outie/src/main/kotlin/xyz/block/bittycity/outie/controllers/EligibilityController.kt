package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import xyz.block.bittycity.outie.client.Eligibility
import xyz.block.bittycity.outie.client.EligibilityClient
import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.models.CheckingEligibility
import xyz.block.bittycity.outie.models.FailureReason.CUSTOMER_IS_INELIGIBLE
import xyz.block.bittycity.outie.models.HoldingSubmission
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import jakarta.inject.Inject
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Operation

class EligibilityController @Inject constructor(
  stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState>,
  withdrawalStore: WithdrawalStore,
  private val eligibilityClient: EligibilityClient,
  private val metricsClient: MetricsClient,
) : WithdrawalController(stateMachine, metricsClient, withdrawalStore) {

  override fun processInputs(
    value: Withdrawal,
    inputs: List<Input<RequirementId>>,
    operation: Operation,
    hurdleGroupId: String?
  ): Result<ProcessingState<Withdrawal, RequirementId>> = result {
    when (value.state) {
      CheckingEligibility -> {
        val eligibility = eligibilityClient.productEligibility(
          value.customerId.id
        ).bind()
        when (eligibility) {
          is Eligibility.Eligible -> value.transitionTo(HoldingSubmission, metricsClient)
          is Eligibility.Ineligible -> value.fail(CUSTOMER_IS_INELIGIBLE, metricsClient)
        }.bind()
      }
      else -> raise(mismatchedState(value))
    }.asProcessingState()
  }

  override fun handleFailure(failure: Throwable, value: Withdrawal): Result<Withdrawal> = result {
    if (value.feeRefunded) {
      logger.error(failure) {
        "An unexpected error occurred checking eligibility for a withdrawal that had been held " +
          "for sanctions review. The withdrawal will not be failed."
      }
      raise(failure)
    } else {
      failWithdrawal(failure, value).bind()
    }
  }
}
