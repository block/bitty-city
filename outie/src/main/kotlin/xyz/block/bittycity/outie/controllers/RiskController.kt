package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.client.RiskClient
import xyz.block.bittycity.outie.client.RiskEvaluation
import xyz.block.bittycity.outie.models.CheckingRisk
import xyz.block.bittycity.outie.models.CheckingTravelRule
import xyz.block.bittycity.outie.models.CollectingScamWarningDecision
import xyz.block.bittycity.outie.models.FailureReason.RISK_BLOCKED
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import jakarta.inject.Inject
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Operation

class RiskController @Inject constructor(
  stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState>,
  withdrawalStore: WithdrawalStore,
  private val riskClient: RiskClient,
  private val metricsClient: MetricsClient,
) : WithdrawalController(stateMachine, metricsClient, withdrawalStore) {

  override fun processInputs(
    value: Withdrawal,
    inputs: List<Input<RequirementId>>,
    operation: Operation,
    hurdleGroupId: String?
  ): Result<ProcessingState<Withdrawal, RequirementId>> = result {
    when (value.state) {
      CheckingRisk -> {
        val riskEvaluation = riskClient.evaluateRisk(value.customerId, value.id).bind()
        when (riskEvaluation) {
          is RiskEvaluation.ActiveScamWarning -> value.transitionTo(
            CollectingScamWarningDecision,
            metricsClient
          )
          is RiskEvaluation.Blocked -> {
            value.fail(RISK_BLOCKED, metricsClient)
            raise(RiskBlocked)
          }
          RiskEvaluation.Checked -> value.transitionTo(CheckingTravelRule, metricsClient)
        }.bind()
      }

      else -> raise(mismatchedState(value))
    }.asProcessingState()
  }

  override fun handleFailure(failure: Throwable, value: Withdrawal): Result<Withdrawal> = result {
    failWithdrawal(failure, value).bind()
  }
}
