package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.Decision
import app.cash.kfsm.Transition
import arrow.core.raise.result
import xyz.block.bittycity.outie.models.CheckingEligibility
import xyz.block.bittycity.outie.models.CheckingRisk
import xyz.block.bittycity.outie.models.CheckingSanctions
import xyz.block.bittycity.outie.models.CheckingTravelRule
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.outie.models.CollectingSanctionsInfo
import xyz.block.bittycity.outie.models.CollectingScamWarningDecision
import xyz.block.bittycity.outie.models.CollectingSelfAttestation
import xyz.block.bittycity.outie.models.ConfirmedComplete
import xyz.block.bittycity.outie.models.Failed
import xyz.block.bittycity.outie.models.CheckingLimits
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.Sanctioned
import xyz.block.bittycity.outie.models.SubmittingOnChain
import xyz.block.bittycity.outie.models.WaitingForConfirmedOnChainStatus
import xyz.block.bittycity.outie.models.WaitingForPendingConfirmationStatus
import xyz.block.bittycity.outie.models.WaitingForSanctionsHeldDecision
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken

abstract class WithdrawalTransition(
  from: Set<WithdrawalState>,
  to: WithdrawalState
) : Transition<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>(from, to) {
  constructor(from: WithdrawalState, to: WithdrawalState) : this(setOf(from), to)

  final override fun decide(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> =
    when (val decision = transitionDecision(value)) {
      is Decision.Accept -> Decision.accept(
        state = decision.state,
        effects = decision.effects + WithdrawalEffect.PublishEvent(value) // TODO: replace with your effect
      )
      is Decision.Reject -> decision
    }

  protected abstract fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect>
}

class Fail(
  private val failureReason: FailureReason
) : WithdrawalTransition(
  from = setOf(
    CollectingInfo,
    CollectingSelfAttestation,
    CheckingRisk,
    CollectingScamWarningDecision,
    CheckingLimits,
    CheckingSanctions,
    SubmittingOnChain,
    WaitingForSanctionsHeldDecision,
    CheckingTravelRule,
    CheckingEligibility,
    WaitingForPendingConfirmationStatus,
    WaitingForConfirmedOnChainStatus
  ),
  to = Failed.placeholder
) {
  override fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> = Decision.accept(
    state = Failed(failureReason),
    effects = listOf(
      WithdrawalEffect.VoidTransaction(value.customerId, value.sourceBalanceToken, value.ledgerTransactionId)
    )
  )
}

class InfoComplete : WithdrawalTransition(
  from = CollectingInfo,
  to = CheckingSanctions
) {
  override fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> = Decision.accept(
    state = CheckingSanctions,
    effects = listOf(WithdrawalEffect.CreateTransaction(value))
  )
}

class SanctionsApprove : WithdrawalTransition(
  from = CheckingSanctions,
  to = CheckingRisk
) {
  override fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> = Decision.accept(
    state = CheckingRisk
  )
}

class SanctionsHold : WithdrawalTransition(
  from = CheckingSanctions,
  to = CollectingSanctionsInfo
) {
  override fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> = Decision.accept(
    state = CollectingSanctionsInfo,
    effects = listOf(WithdrawalEffect.RefundFee(value))
  )
}

class SanctionsInfoComplete : WithdrawalTransition(
  from = CollectingSanctionsInfo,
  to = WaitingForSanctionsHeldDecision
) {
  override fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> = Decision.accept(
    state = WaitingForSanctionsHeldDecision
  )
}

class SanctionsHeldApprove : WithdrawalTransition(
  from = setOf(CollectingSanctionsInfo, WaitingForSanctionsHeldDecision),
  to = CheckingEligibility
) {
  override fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> = Decision.accept(
    state = CheckingEligibility
  )
}

class SanctionsHeldSanction : WithdrawalTransition(
  from = setOf(CollectingSanctionsInfo, WaitingForSanctionsHeldDecision),
  to = CheckingEligibility
) {
  override fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> {
    val ledgerTransactionId = value.ledgerTransactionId
    return if (ledgerTransactionId != null) {
      Decision.accept(
        state = Sanctioned,
        effects = listOf(WithdrawalEffect.FreezeFunds(value))
      )
    } else {
      Decision.reject("ledger transaction id is null")
    }
  }
}

class RiskApprove : WithdrawalTransition(
  from = CheckingRisk,
  to = CheckingTravelRule
) {
  override fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> = Decision.accept(
    state = CheckingTravelRule
  )
}

class RiskScamWarning : WithdrawalTransition(
  from = CheckingRisk,
  to = CollectingScamWarningDecision
) {
  override fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> = Decision.accept(
    state = CollectingScamWarningDecision
  )
}

class ScamWarningAcceptRisk : WithdrawalTransition(
  from = CollectingScamWarningDecision,
  to = CheckingTravelRule
) {
  override fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> = Decision.accept(
    state = CheckingTravelRule
  )
}

class SelfAttestationNotRequired : WithdrawalTransition(
  from = CheckingTravelRule,
  to = CheckingEligibility
) {
  override fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> = Decision.accept(
    state = CheckingEligibility
  )
}

class SelfAttestationRequired : WithdrawalTransition(
  from = CheckingTravelRule,
  to = CollectingSelfAttestation
) {
  override fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> = Decision.accept(
    state = CollectingSelfAttestation
  )
}

class SelfAttestationComplete :  WithdrawalTransition(
  from = CollectingSelfAttestation,
  to = CheckingEligibility
) {
  override fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> = Decision.accept(
    state = CheckingEligibility
  )
}

class IsEligible : WithdrawalTransition(
  from = CheckingEligibility,
  to = CheckingLimits
) {
  override fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> = Decision.accept(
    state = CheckingLimits
  )
}

class NotLimited : WithdrawalTransition(
  from = CheckingLimits,
  to = SubmittingOnChain
) {
  override fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> = Decision.accept(
    state = SubmittingOnChain
  )
}

class SubmittedOnChain : WithdrawalTransition(
  from = SubmittingOnChain,
  to = WaitingForPendingConfirmationStatus
) {
  override fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> {
    return result {
      val targetWallet =
        value.targetWalletAddress ?: raise(IllegalArgumentException("Target wallet address is required"))
      val withdrawalAmount = value.amount ?: raise(IllegalArgumentException("Amount is required"))
      val selectedSpeed =
        value.selectedSpeed ?: raise(IllegalArgumentException("Selected speed is required"))
      val withdrawalFee = selectedSpeed.totalFee
      Decision.accept(
        state = WaitingForPendingConfirmationStatus,
        effects = listOf<WithdrawalEffect>(
          WithdrawalEffect.SubmitOnChain(
            withdrawalToken = value.id,
            customerId = value.customerId,
            targetWalletAddress = targetWallet,
            amount = withdrawalAmount,
            fee = withdrawalFee,
            selectedSpeed = selectedSpeed.speed,
          )
        )
      )
    }.getOrElse {
      Decision.reject(it.message ?: "There was an error submitting on chain")
    }
  }
}

class ConfirmedOnChain : WithdrawalTransition(
  from = setOf(WaitingForPendingConfirmationStatus, WaitingForConfirmedOnChainStatus),
  to = ConfirmedComplete
) {
  override fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> {
    return result {
      val ledgerTransactionId =
        value.ledgerTransactionId ?: raise(IllegalArgumentException("Ledger transaction id is required"))
      Decision.accept(
        state = ConfirmedComplete,
        effects = listOf<WithdrawalEffect>(
          WithdrawalEffect.ConfirmTransaction(
            customerId = value.customerId,
            balanceId = value.sourceBalanceToken,
            ledgerTransactionId = ledgerTransactionId,
          )
        )
      )
    }.getOrElse {
      Decision.reject(it.message ?: "There was an error processing the withdrawal's confirmation on chain")
    }
  }
}

class ObservedInMempool :  WithdrawalTransition(
  from = WaitingForPendingConfirmationStatus,
  to = WaitingForConfirmedOnChainStatus
) {
  override fun transitionDecision(value: Withdrawal): Decision<WithdrawalState, WithdrawalEffect> = Decision.accept(
    state = WaitingForConfirmedOnChainStatus
  )
}
