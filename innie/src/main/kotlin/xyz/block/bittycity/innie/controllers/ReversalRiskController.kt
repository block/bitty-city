package xyz.block.bittycity.innie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject
import xyz.block.bittycity.common.client.RiskBlocked
import xyz.block.bittycity.common.client.RiskClient
import xyz.block.bittycity.common.client.RiskEvaluation
import xyz.block.bittycity.innie.client.MetricsClient
import xyz.block.bittycity.innie.models.CheckingReversalRisk
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositReversalFailureReason.RISK_BLOCKED
import xyz.block.bittycity.innie.models.DepositReversalFailureReason.UNEXPECTED_RISK_RESULT
import xyz.block.bittycity.innie.models.DepositReversalToken
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.models.WaitingForReversalPendingConfirmationStatus
import xyz.block.bittycity.innie.store.DepositStore
import xyz.block.bittycity.innie.validation.ParameterIsRequired
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Operation

class ReversalRiskController @Inject constructor(
  stateMachine: StateMachine<DepositToken, Deposit, DepositState>,
  depositStore: DepositStore,
  private val riskClient: RiskClient<DepositReversalToken>,
  private val metricsClient: MetricsClient,
): DepositController(stateMachine, metricsClient, depositStore) {
  override val logger: KLogger = KotlinLogging.logger {}

  override fun processInputs(
    value: Deposit,
    inputs: List<Input<RequirementId>>,
    operation: Operation,
    hurdleGroupId: String?
  ): Result<ProcessingState<Deposit, RequirementId>> = result {
    when (value.state) {
      CheckingReversalRisk -> {
        val reversal = value.currentReversal ?: raise(ParameterIsRequired(value.customerId, "currentReversal"))
        val riskResult = riskClient.evaluateRisk(value.customerId, reversal.token).bind()
        when (riskResult) {
          is RiskEvaluation.ActiveScamWarning -> {
            value.failReversal(UNEXPECTED_RISK_RESULT, metricsClient)
            raise(IllegalStateException("Unexpected risk reversal result: $riskResult"))
          }
          RiskEvaluation.Checked -> value.transitionTo(WaitingForReversalPendingConfirmationStatus, metricsClient)
          is RiskEvaluation.Blocked -> {
            value.failReversal(RISK_BLOCKED, metricsClient)
            raise(RiskBlocked)
          }
        }.bind()
      }
      else -> raise(mismatchedState(value))
    }.asProcessingState()
  }

  override fun handleFailure(
    failure: Throwable,
    value: Deposit
  ): Result<Deposit> = failReversal(failure, value)

}
