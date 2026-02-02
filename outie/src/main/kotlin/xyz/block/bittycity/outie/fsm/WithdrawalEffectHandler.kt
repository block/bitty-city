package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.EffectHandler
import app.cash.kfsm.EffectOutcome
import arrow.core.raise.result
import jakarta.inject.Inject
import xyz.block.bittycity.common.client.Eligibility
import xyz.block.bittycity.common.client.EligibilityClient
import xyz.block.bittycity.common.client.Evaluation
import xyz.block.bittycity.common.client.RiskBlocked
import xyz.block.bittycity.common.client.RiskClient
import xyz.block.bittycity.common.client.RiskEvaluation
import xyz.block.bittycity.common.client.SanctionsClient
import xyz.block.bittycity.outie.client.EventClient
import xyz.block.bittycity.outie.client.LedgerClient
import xyz.block.bittycity.outie.client.LimitClient
import xyz.block.bittycity.outie.client.LimitResponse
import xyz.block.bittycity.outie.client.OnChainClient
import xyz.block.bittycity.outie.client.TravelRuleClient
import xyz.block.bittycity.outie.client.WithdrawRequest
import xyz.block.bittycity.outie.client.WithdrawalEvent
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken

class WithdrawalEffectHandler @Inject constructor(
  private val sanctionsClient: SanctionsClient<WithdrawalToken>,
  private val riskClient: RiskClient<WithdrawalToken>,
  private val travelRuleClient: TravelRuleClient,
  private val eligibilityClient: EligibilityClient,
  private val limitsClient: LimitClient,
  private val onChainClient: OnChainClient,
  private val ledgerClient: LedgerClient,
  private val eventClient: EventClient
) : EffectHandler<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect> {

  override fun handle(
    valueId: WithdrawalToken,
    effect: WithdrawalEffect
  ): Result<EffectOutcome<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>> = when (effect) {
    is WithdrawalEffect.ConfirmTransaction -> handleConfirmTransaction(effect)
    is WithdrawalEffect.CreateTransaction -> handleCreateTransaction(effect)
    is WithdrawalEffect.FreezeFunds -> handleFreezeFunds(effect)
    is WithdrawalEffect.RefundFee -> handleRefundFee(effect)
    is WithdrawalEffect.SubmitOnChain -> handleSubmitOnChain(valueId, effect)
    is WithdrawalEffect.VoidTransaction -> handleVoidTransaction(effect)
    is WithdrawalEffect.CheckSanctions -> handleCheckSanctions(valueId, effect)
    is WithdrawalEffect.CheckRisk -> handleCheckRisk(valueId, effect)
    is WithdrawalEffect.CheckTravelRule -> handleCheckTravelRule(valueId, effect)
    is WithdrawalEffect.CheckEligibility -> handleCheckEligibility(valueId, effect)
    is WithdrawalEffect.CheckLimits -> handleCheckLimits(valueId, effect)
    is WithdrawalEffect.PublishEvent -> handlePublishEvent(effect)
  }

  private fun handleConfirmTransaction(
    effect: WithdrawalEffect.ConfirmTransaction
  ): Result<EffectOutcome<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>> = result {
    ledgerClient.confirmTransaction(
      effect.customerId,
      effect.balanceId,
      effect.ledgerTransactionId
    ).bind()
    EffectOutcome.Completed
  }

  private fun handleCreateTransaction(
    effect: WithdrawalEffect.CreateTransaction
  ): Result<EffectOutcome<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>> = result {
    ledgerClient.createTransaction(effect.withdrawal).bind()
    EffectOutcome.Completed
  }

  private fun handleFreezeFunds(
    effect: WithdrawalEffect.FreezeFunds
  ): Result<EffectOutcome<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>> = result {
    ledgerClient.freezeFunds(effect.withdrawal).bind()
    EffectOutcome.Completed
  }

  private fun handleRefundFee(
    effect: WithdrawalEffect.RefundFee
  ): Result<EffectOutcome<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>> = result {
    ledgerClient.refundFee(effect.withdrawal).bind()
    EffectOutcome.Completed
  }

  private fun handleSubmitOnChain(
    valueId: WithdrawalToken,
    effect: WithdrawalEffect.SubmitOnChain
  ): Result<EffectOutcome<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>> = result {
    val request = WithdrawRequest(
      withdrawalToken = effect.withdrawalToken,
      customerId = effect.customerId,
      destinationAddress = effect.targetWalletAddress,
      amount = effect.amount,
      fee = effect.fee,
      speed = effect.selectedSpeed,
      metadata = emptyMap()
    )
    onChainClient.submitWithdrawal(request).bind()
    EffectOutcome.TransitionProduced(valueId, SubmittedOnChain())
  }

  private fun handleVoidTransaction(
    effect: WithdrawalEffect.VoidTransaction
  ): Result<EffectOutcome<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>> = result {
    effect.ledgerTransactionId?.let {
      ledgerClient.voidTransaction(
        customerId = effect.customerId,
        balanceId = effect.balanceId,
        ledgerTransactionId = it
      ).bind()
    }
    EffectOutcome.Completed
  }

  private fun handleCheckSanctions(
    valueId: WithdrawalToken,
    effect: WithdrawalEffect.CheckSanctions
  ): Result<EffectOutcome<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>> = result {
    val sanctionsResult = sanctionsClient.evaluateSanctions(
      customerId = effect.customerId.id,
      transactionToken = effect.withdrawalToken,
      targetWalletAddress = effect.targetWalletAddress,
      amount = effect.amount
    ).bind()

    when (sanctionsResult) {
      Evaluation.APPROVE -> EffectOutcome.TransitionProduced(valueId, SanctionsApprove())
      Evaluation.FAIL -> {
        EffectOutcome.FailedWithTransition(valueId, Fail(FailureReason.SANCTIONS_FAILED), "Risk blocked")
        raise(RiskBlocked)
      }
      Evaluation.HOLD -> EffectOutcome.TransitionProduced(valueId, SanctionsHold())
    }
  }

  private fun handleCheckRisk(
    valueId: WithdrawalToken,
    effect: WithdrawalEffect.CheckRisk
  ): Result<EffectOutcome<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>> = result {
    val riskEvaluation = riskClient.evaluateRisk(effect.customerId, effect.withdrawalToken).bind()
    when (riskEvaluation) {
      is RiskEvaluation.ActiveScamWarning -> EffectOutcome.TransitionProduced(valueId, RiskScamWarning())
      is RiskEvaluation.Blocked -> {
        EffectOutcome.FailedWithTransition(valueId, Fail(FailureReason.RISK_BLOCKED), "Risk blocked")
        raise(RiskBlocked)
      }
      RiskEvaluation.Checked -> EffectOutcome.TransitionProduced(valueId, RiskApprove())
    }
  }

  private fun handleCheckTravelRule(
    valueId: WithdrawalToken,
    effect: WithdrawalEffect.CheckTravelRule
  ): Result<EffectOutcome<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>> = result {
    val requiresSelfAttestation =
      travelRuleClient.requireSelfAttestation(
        effect.targetWalletAddress,
        effect.fiatAmount,
        effect.customerId
      ).onFailure { failure ->
        // If we fail to determine if self-attestation is required then we fail the withdrawal
        EffectOutcome.FailedWithTransition(valueId, Fail(FailureReason.UNKNOWN), "Could not determine if self attestation is required")
      }.bind()

    if (requiresSelfAttestation) {
      EffectOutcome.TransitionProduced(valueId, SelfAttestationRequired())
    } else {
      EffectOutcome.TransitionProduced(valueId, SelfAttestationNotRequired())
    }
  }

  private fun handleCheckEligibility(
    valueId: WithdrawalToken,
    effect: WithdrawalEffect.CheckEligibility
  ): Result<EffectOutcome<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>> = result {
    val eligibility = eligibilityClient.productEligibility(
      effect.customerId.id
    ).bind()
    when (eligibility) {
      is Eligibility.Eligible -> EffectOutcome.TransitionProduced(valueId, IsEligible())
      is Eligibility.Ineligible -> EffectOutcome.FailedWithTransition(valueId, Fail(FailureReason.CUSTOMER_IS_INELIGIBLE), "Customer is ineligible")
    }
  }

  private fun handleCheckLimits(
    valueId: WithdrawalToken,
    effect: WithdrawalEffect.CheckLimits
  ): Result<EffectOutcome<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>> = result {
    effect.withdrawal.customerId
    val limitResult = limitsClient.evaluateLimits(effect.withdrawal.customerId, effect.withdrawal).bind()
    when (limitResult) {
      is LimitResponse.NotLimited -> EffectOutcome.TransitionProduced(valueId, NotLimited())
      is LimitResponse.Limited -> EffectOutcome.FailedWithTransition(valueId, Fail(FailureReason.LIMITED), "Customer is over limits")
    }
  }

  private fun handlePublishEvent(
    effect: WithdrawalEffect.PublishEvent
  ): Result<EffectOutcome<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>> = result {
    val withdrawalEvent = WithdrawalEvent(
      withdrawalToken = effect.oldWithdrawal.id,
      newWithdrawal = effect.newWithdrawal,
      oldWithdrawal = effect.oldWithdrawal,
      eventType = effect.eventType
    )
    eventClient.publish(withdrawalEvent).bind()
    EffectOutcome.Completed
  }
}