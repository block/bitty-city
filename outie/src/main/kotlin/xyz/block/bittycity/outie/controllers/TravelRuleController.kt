package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.client.TravelRuleClient
import xyz.block.bittycity.outie.models.CheckingEligibility
import xyz.block.bittycity.outie.models.CheckingTravelRule
import xyz.block.bittycity.outie.models.CollectingSelfAttestation
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import xyz.block.bittycity.outie.validation.ParameterIsRequired
import jakarta.inject.Inject
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Operation

class TravelRuleController @Inject constructor(
  stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState>,
  withdrawalStore: WithdrawalStore,
  private val travelRuleClient: TravelRuleClient,
  private val metricsClient: MetricsClient,
) : WithdrawalController(stateMachine, metricsClient, withdrawalStore) {

  override fun processInputs(
    value: Withdrawal,
    inputs: List<Input<RequirementId>>,
    operation: Operation,
    hurdleGroupId: String?
  ): Result<ProcessingState<Withdrawal, RequirementId>> = result {
    when (value.state) {
      CheckingTravelRule -> {
        val amount =
          value.fiatEquivalentAmount ?: raise(ParameterIsRequired(value.customerId, "amount"))
        val targetWalletAddress = value.targetWalletAddress ?: raise(
          ParameterIsRequired(value.customerId, "targetWalletAddress")
        )

        val requiresSelfAttestation =
          travelRuleClient.requireSelfAttestation(
            targetWalletAddress,
            amount,
            value.customerId
          ).onFailure { failure ->
            // If we fail to determine if self-attestation is required then we fail the withdrawal
            value.fail(failure.toFailureReason(), metricsClient).bind()
          }.bind()

        value.transitionTo(
          state = if (requiresSelfAttestation) CollectingSelfAttestation else CheckingEligibility,
          metricsClient = metricsClient,
        ).bind()
      }

      else -> raise(mismatchedState(value))
    }.asProcessingState()
  }

  override fun handleFailure(failure: Throwable, value: Withdrawal): Result<Withdrawal> = result {
    failWithdrawal(failure, value).bind()
  }
}
