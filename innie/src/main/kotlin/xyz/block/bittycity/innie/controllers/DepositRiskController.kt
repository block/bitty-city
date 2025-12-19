package xyz.block.bittycity.innie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject
import xyz.block.bittycity.common.client.RiskClient
import xyz.block.bittycity.common.client.RiskEvaluation
import xyz.block.bittycity.innie.client.MetricsClient
import xyz.block.bittycity.innie.models.CheckingDepositRisk
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositFailureReason
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.models.Settled
import xyz.block.bittycity.innie.store.DepositStore
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Operation

class DepositRiskController @Inject constructor(
  stateMachine: StateMachine<DepositToken, Deposit, DepositState>,
  depositStore: DepositStore,
  private val riskClient: RiskClient<DepositToken>,
  private val metricsClient: MetricsClient,
) : DepositController(stateMachine, metricsClient, depositStore) {
  override val logger: KLogger = KotlinLogging.logger {}

  override fun processInputs(
    value: Deposit,
    inputs: List<Input<RequirementId>>,
    operation: Operation,
    hurdleGroupId: String?
  ): Result<ProcessingState<Deposit, RequirementId>> = result {
    when (value.state) {
      CheckingDepositRisk -> {
        val riskResult = riskClient.evaluateRisk(value.customerId, value.id).bind()
        when (riskResult) {
          is RiskEvaluation.ActiveScamWarning -> {
            logger.warn { "Unexpected scam result: $riskResult" }
            value.transitionTo(Settled, metricsClient)
          }
          RiskEvaluation.Checked -> value.transitionTo(Settled, metricsClient)
          is RiskEvaluation.Blocked -> value.fail(DepositFailureReason.RISK_BLOCKED, metricsClient)
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
      "An unexpected error occurred doing risk checks for an on-chain deposit. The deposit will not be failed."
    }
    raise(failure)
  }
}
