package xyz.block.bittycity.innie.fsm

import app.cash.kfsm.v2.EffectHandler
import app.cash.kfsm.v2.EffectOutcome
import arrow.core.raise.result
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject
import xyz.block.bittycity.common.client.Eligibility
import xyz.block.bittycity.common.client.Evaluation
import xyz.block.bittycity.innie.client.DepositEligibilityClient
import xyz.block.bittycity.common.client.RiskClient
import xyz.block.bittycity.common.client.RiskEvaluation
import xyz.block.bittycity.common.client.SanctionsClient
import xyz.block.bittycity.innie.client.DepositLedgerClient
import xyz.block.bittycity.innie.client.MetricsClient
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositFailureReason
import xyz.block.bittycity.innie.models.DepositReversalFailureReason
import xyz.block.bittycity.innie.models.DepositReversalFailureReason.RISK_BLOCKED
import xyz.block.bittycity.innie.models.DepositReversalFailureReason.SANCTIONS_FAILED
import xyz.block.bittycity.innie.models.DepositReversalToken
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken

class DepositEffectHandler @Inject constructor(
  private val ledgerClient: DepositLedgerClient,
  private val depositRiskClient: RiskClient<DepositToken>,
  private val reversalRiskClient: RiskClient<DepositReversalToken>,
  private val eligibilityClient: DepositEligibilityClient,
  private val sanctionsClient: SanctionsClient<DepositReversalToken>,
  private val metricsClient: MetricsClient,
):
  EffectHandler<DepositToken, Deposit, DepositState, DepositEffect> {

  val logger: KLogger = KotlinLogging.logger {}

  override fun handle(
    valueId: DepositToken,
    effect: DepositEffect
  ): Result<EffectOutcome<DepositToken, Deposit, DepositState, DepositEffect>> = when (effect) {
    is DepositEffect.ConfirmDepositTransaction -> handleConfirmDepositTransaction(effect)
    is DepositEffect.ConfirmReversalTransaction -> handleConfirmReversalTransaction(effect)
    is DepositEffect.RequestDepositTransactionCreation -> handleRequestDepositTransactionCreation(valueId, effect)
    is DepositEffect.FreezeReversal -> handleFreezeReversal(effect)
    is DepositEffect.RequestDepositEligibilityCheck -> handleRequestDepositEligibilityCheck(valueId, effect)
    is DepositEffect.RequestReversalRiskCheck -> handleRequestReversalRiskCheck(valueId, effect)
    is DepositEffect.RequestReversalSanctionsCheck -> handleRequestReversalSanctionsCheck(valueId, effect)
    is DepositEffect.RequestDepositRiskCheck -> handleRequestDepositRiskCheck(valueId, effect)
    is DepositEffect.VoidDepositTransaction -> handleVoidDepositTransaction(effect)
    is DepositEffect.PublishStateTransitionMetric -> handlePublishStateTransitionMetric(effect)
    is DepositEffect.PublishFailureReasonMetric -> handlePublishFailureReasonMetric(effect.reason)
    is DepositEffect.PublishReversalFailureReasonMetric ->
      handlePublishReversalFailureReasonMetric(effect.reason)
    is DepositEffect.PublishDepositSuccessAmountMetric -> handlePublishDepositSuccessAmountMetric(effect.deposit)
  }

  private fun handleConfirmDepositTransaction(
    effect: DepositEffect.ConfirmDepositTransaction
  ): Result<EffectOutcome<DepositToken, Deposit, DepositState, DepositEffect>> = result {
    ledgerClient.confirmDepositTransaction(
      effect.customerId,
      effect.balanceId,
      effect.ledgerTransactionId
    ).bind()
    EffectOutcome.Completed
  }

  private fun handleRequestDepositRiskCheck(
    valueId: DepositToken,
    effect: DepositEffect.RequestDepositRiskCheck
  ): Result<EffectOutcome<DepositToken, Deposit, DepositState, DepositEffect>> = result {
    val riskResult = depositRiskClient.evaluateRisk(effect.customerId, effect.depositId).bind()
    when (riskResult) {
      is RiskEvaluation.ActiveScamWarning -> {
        logger.warn { "Unexpected scam result: $riskResult" }
        EffectOutcome.FailedWithTransition(
          valueId,
          DepositFailed(DepositFailureReason.UNEXPECTED_RISK_RESULT),
          "Unexpected risk result"
        )
      }

      RiskEvaluation.Checked -> EffectOutcome.TransitionProduced(valueId, DepositRiskApproved())
      is RiskEvaluation.Blocked -> EffectOutcome.FailedWithTransition(
        valueId,
        DepositFailed(DepositFailureReason.RISK_BLOCKED),
        "Risk blocked"
      )
    }
  }

  private fun handleRequestDepositEligibilityCheck(
    valueId: DepositToken,
    effect: DepositEffect.RequestDepositEligibilityCheck
  ): Result<EffectOutcome<DepositToken, Deposit, DepositState, DepositEffect>> = result {
    val eligibility = eligibilityClient.productEligibility(
      effect.customerId.id
    ).bind()
    when (eligibility) {
      is Eligibility.Eligible -> EffectOutcome.TransitionProduced(valueId, IsEligibleForDeposit())
      is Eligibility.Ineligible -> EffectOutcome.FailedWithTransition(
        valueId,
        DepositFailed(DepositFailureReason.INELIGIBLE),
        "Ineligible to deposit"
      )
    }
  }

  private fun handleRequestReversalRiskCheck(
    valueId: DepositToken,
    effect: DepositEffect.RequestReversalRiskCheck
  ): Result<EffectOutcome<DepositToken, Deposit, DepositState, DepositEffect>> = result {
    val riskResult = reversalRiskClient.evaluateRisk(effect.customerId, effect.depositReversalId).bind()
    when (riskResult) {
      is RiskEvaluation.ActiveScamWarning -> {
        logger.warn { "Unexpected scam result: $riskResult" }
        EffectOutcome.TransitionProduced(valueId, ReversalFailed(RISK_BLOCKED))
      }
      RiskEvaluation.Checked -> EffectOutcome.TransitionProduced(valueId, ReversalRiskApproved())
      is RiskEvaluation.Blocked -> EffectOutcome.TransitionProduced(valueId, ReversalFailed(RISK_BLOCKED))
    }
  }

  private fun handleRequestReversalSanctionsCheck(
    valueId: DepositToken,
    effect: DepositEffect.RequestReversalSanctionsCheck
  ): Result<EffectOutcome<DepositToken, Deposit, DepositState, DepositEffect>> = result {
    val sanctionsResult = sanctionsClient.evaluateSanctions(
      customerId = effect.customerId.id,
      transactionToken = effect.reversalId,
      targetWalletAddress = effect.reversalTargetWalletAddress,
      amount = effect.amount
    ).bind()

    when (sanctionsResult) {
      Evaluation.APPROVE -> EffectOutcome.TransitionProduced(valueId, ReversalSanctionsApproved())
      Evaluation.FAIL -> EffectOutcome.TransitionProduced(valueId, ReversalFailed(SANCTIONS_FAILED))
      Evaluation.HOLD -> EffectOutcome.TransitionProduced(valueId, ReversalSanctionsHold())
    }
  }

  private fun handleConfirmReversalTransaction(
    effect: DepositEffect.ConfirmReversalTransaction
  ): Result<EffectOutcome<DepositToken, Deposit, DepositState, DepositEffect>> = result {
    ledgerClient.confirmReversalTransaction(
      effect.customerId,
      effect.balanceId,
      effect.ledgerTransactionId
    ).bind()
    EffectOutcome.Completed
  }

  private fun handleRequestDepositTransactionCreation(
    valueId: DepositToken,
    effect: DepositEffect.RequestDepositTransactionCreation
  ) : Result<EffectOutcome<DepositToken, Deposit, DepositState, DepositEffect>> = result {
    val transactionId = ledgerClient.createDepositTransaction(
      effect.depositId,
      effect.customerId,
      effect.balanceId,
      effect.createdAt,
      effect.amount,
      effect.fiatEquivalent
    ).bind()
    EffectOutcome.TransitionProduced(valueId, DepositTransactionCreated(transactionId))
  }

  private fun handleVoidDepositTransaction(
    effect: DepositEffect.VoidDepositTransaction
  ): Result<EffectOutcome<DepositToken, Deposit, DepositState, DepositEffect>> = result {
    effect.ledgerTransactionId?.let { transactionId ->
      ledgerClient.voidDepositTransaction(
        effect.customerId,
        effect.balanceId,
        transactionId
      ).bind()
    }
    EffectOutcome.Completed
  }

  private fun handleFreezeReversal(
    effect: DepositEffect.FreezeReversal
  ): Result<EffectOutcome<DepositToken, Deposit, DepositState, DepositEffect>> = result {
    ledgerClient.freezeFunds(
      depositReversalId = effect.depositReversalId,
      customerId = effect.customerId,
      balanceId = effect.balanceId,
      createdAt = effect.createdAt,
      amount = effect.amount,
      fiatEquivalent = effect.fiatEquivalent,
      targetWalletAddress = effect.targetWalletAddress,
      ledgerTransactionId = effect.ledgerTransactionId
    )
    EffectOutcome.Completed
  }

  private fun handlePublishStateTransitionMetric(
    effect: DepositEffect.PublishStateTransitionMetric
  ): Result<EffectOutcome<DepositToken, Deposit, DepositState, DepositEffect>> = result {
    metricsClient.stateTransition(effect.from, effect.to, effect.failureReason)
      .recover { logger.warn(it) { "Failure to publish deposit state transition metrics" } }
    EffectOutcome.Completed
  }

  private fun handlePublishFailureReasonMetric(
    reason: DepositFailureReason
  ): Result<EffectOutcome<DepositToken, Deposit, DepositState, DepositEffect>> = result {
    metricsClient.failureReason(reason)
      .recover { logger.warn(it) { "Failure to publish deposit failure reason metrics" } }
    EffectOutcome.Completed
  }

  private fun handlePublishReversalFailureReasonMetric(
    reason: DepositReversalFailureReason
  ): Result<EffectOutcome<DepositToken, Deposit, DepositState, DepositEffect>> = result {
    metricsClient.reversalFailureReason(reason)
      .recover { logger.warn(it) { "Failure to publish deposit reversal failure reason metrics" } }
    EffectOutcome.Completed
  }

  private fun handlePublishDepositSuccessAmountMetric(
    deposit: Deposit
  ): Result<EffectOutcome<DepositToken, Deposit, DepositState, DepositEffect>> = result {
    metricsClient.depositSuccessAmount(deposit)
      .recover { logger.warn(it) { "Failure to publish deposit success amount metrics" } }
    EffectOutcome.Completed
  }
}
