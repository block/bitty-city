package xyz.block.bittycity.innie.fsm

import app.cash.kfsm.v2.Decision
import app.cash.kfsm.v2.Transition
import xyz.block.bittycity.common.models.LedgerTransactionId
import xyz.block.bittycity.innie.models.CheckingDepositRisk
import xyz.block.bittycity.innie.models.CheckingDepositEligibility
import xyz.block.bittycity.innie.models.CheckingReversalRisk
import xyz.block.bittycity.innie.models.CheckingReversalSanctions
import xyz.block.bittycity.innie.models.CollectingReversalInfo
import xyz.block.bittycity.innie.models.CollectingReversalSanctionsInfo
import xyz.block.bittycity.innie.models.CreatingDepositTransaction
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.DepositExpiredPending
import xyz.block.bittycity.innie.models.DepositFailureReason
import xyz.block.bittycity.innie.models.DepositReversalFailureReason
import xyz.block.bittycity.innie.models.DepositSettled
import xyz.block.bittycity.innie.models.DepositVoided
import xyz.block.bittycity.innie.models.WaitingForDepositConfirmedOnChainStatus
import xyz.block.bittycity.innie.models.New
import xyz.block.bittycity.innie.models.ReversalConfirmedComplete
import xyz.block.bittycity.innie.models.ReversalSanctioned
import xyz.block.bittycity.innie.models.WaitingForReversalConfirmedOnChainStatus
import xyz.block.bittycity.innie.models.WaitingForReversalPendingConfirmationStatus
import xyz.block.bittycity.innie.models.WaitingForReversalSanctionsHeldDecision
import java.time.Instant

abstract class DepositTransition(
  from: Set<DepositState>,
  to: DepositState
) : Transition<DepositToken, Deposit, DepositState, DepositEffect>(from, to) {
  constructor(from: DepositState, to: DepositState) : this(setOf(from), to)

  final override fun decide(value: Deposit): Decision<Deposit, DepositState, DepositEffect> =
    when (val decision = transitionDecision(value)) {
      is Decision.Accept -> Decision.accept(
        value = decision.value,
        effects = decision.effects // Other effects that run for every transition can be added here
      )
      is Decision.Reject -> decision
    }

  protected abstract fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect>
}

class DepositObservedInMempool() : DepositTransition(
  from = New,
  to = WaitingForDepositConfirmedOnChainStatus
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(state = WaitingForDepositConfirmedOnChainStatus),
    effects = listOf(
      DepositEffect.RequestDepositTransactionCreation(
        depositId = value.id,
        customerId = value.customerId,
        balanceId = value.targetBalanceToken,
        createdAt = value.createdAt ?: Instant.now(),
        amount = value.amount,
        fiatEquivalent = value.fiatEquivalentAmount,
      )
    )
  )
}

class DepositTransactionCreated(
  val ledgerTransactionId: LedgerTransactionId
) : DepositTransition(
  from = CreatingDepositTransaction,
  to = WaitingForDepositConfirmedOnChainStatus
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(
      state = WaitingForDepositConfirmedOnChainStatus,
      ledgerTransactionId = ledgerTransactionId
    )
  )
}

/**
 * Deposits that remain pending on-chain for too long are considered expired locally. Unlike Voided, these may later
 * transition to ConfirmedOnBlockchain if a late confirmation arrives.
 */
class DepositExpiredLocally() : DepositTransition(
  from = WaitingForDepositConfirmedOnChainStatus,
  to = DepositExpiredPending
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(state = DepositExpiredPending)
  )
}

class DepositVoided() : DepositTransition(
  from = setOf(WaitingForDepositConfirmedOnChainStatus, DepositExpiredPending),
  to = DepositVoided
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(state = DepositVoided),
    effects = listOf(
      DepositEffect.VoidDepositTransaction(value.customerId, value.targetBalanceToken, value.ledgerTransactionId)
    )
  )
}

class DepositConfirmedOnChain() : DepositTransition(
  from = setOf(WaitingForDepositConfirmedOnChainStatus, DepositExpiredPending),
  to = CheckingDepositEligibility
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(state = CheckingDepositEligibility),
    effects = listOf(
      DepositEffect.RequestDepositEligibilityCheck(value.customerId)
    )
  )
}

class IsEligibleForDeposit() : DepositTransition(
  from = setOf(CheckingDepositEligibility),
  to = CheckingDepositRisk
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(state = CheckingDepositRisk),
    effects = listOf(
      DepositEffect.RequestDepositRiskCheck(value.customerId, value.id)
    )
  )
}

class IsEligible() : DepositTransition(
  from = setOf(CheckingDepositEligibility),
  to = CheckingDepositRisk
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(state = CheckingDepositRisk)
  )
}

class DepositFailed(
  val reason: DepositFailureReason
) : DepositTransition(
  from = setOf(CheckingDepositEligibility, CheckingDepositRisk),
  to = CollectingReversalInfo
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(failureReason = reason, state = CollectingReversalInfo)
  )
}

class DepositRiskApproved() : DepositTransition(
  from = setOf(CheckingDepositRisk),
  to = DepositSettled
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> {
    val transactionId = value.ledgerTransactionId

    return if (transactionId != null) {
      Decision.accept(
        value = value.copy(state = DepositSettled),
        effects = listOf(
          DepositEffect.ConfirmDepositTransaction(value.customerId, value.targetBalanceToken, transactionId)
        )
      )
    } else {
      Decision.reject("Cannot settle deposit with no ledger transaction id")
    }
  }
}

class ReversalFailed(
  val reason: DepositReversalFailureReason
) : DepositTransition(
  from = setOf(
    CheckingReversalSanctions,
    CheckingReversalRisk,
    WaitingForReversalPendingConfirmationStatus,
    WaitingForReversalConfirmedOnChainStatus,
    WaitingForReversalSanctionsHeldDecision
  ),
  to = CollectingReversalInfo
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> {
    val currentReversal = value.currentReversal
    return if (currentReversal != null) {
      Decision.accept(
        value = value.updateCurrentReversal {
          it.copy(failureReason = reason)
        }.getOrNull()!!
          .copy(state = CollectingReversalInfo)
      )
    } else  {
      Decision.reject("Cannot fail a reversal that doesn't exist")
    }
  }
}

class ReversalInfoCollectionComplete : DepositTransition(
  from = setOf(CollectingReversalInfo),
  to = CheckingReversalSanctions
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> {
    val targetWalletAddress = value.reversals.lastOrNull()?.targetWalletAddress
    val reversalId = value.reversals.lastOrNull()?.token
    return if (targetWalletAddress != null && reversalId != null) {
      Decision.accept(
        value = value.copy(state = CheckingReversalSanctions),
        effects = listOf(
          DepositEffect.RequestReversalSanctionsCheck(value.customerId, reversalId, targetWalletAddress, value.amount)
        )
      )
    } else {
      Decision.reject("Cannot check sanctions for a reversal with no target wallet address or reversal id")
    }
  }
}

class ReversalSanctionsApproved : DepositTransition(
  from = setOf(CheckingReversalSanctions, WaitingForReversalSanctionsHeldDecision),
  to = CheckingReversalRisk
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> {
    val reversalId = value.reversals.lastOrNull()?.token
    return if (reversalId != null) {
      Decision.accept(
        value = value.copy(state = CheckingReversalRisk),
        effects = listOf(DepositEffect.RequestReversalRiskCheck(value.customerId, reversalId))
      )
    } else {
      Decision.reject("Cannot check risk for a reversal with no id")
    }
  }
}

class ReversalSanctionsDecisionFrozen : DepositTransition(
  from = setOf(WaitingForReversalSanctionsHeldDecision),
  to = ReversalSanctioned
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> {
    val currentReversal = value.currentReversal
    val targetWalletAddress = currentReversal?.targetWalletAddress
    return if (currentReversal != null && targetWalletAddress != null) {
      Decision.accept(
        value = value.copy(state = ReversalSanctioned),
        effects = listOf(
          DepositEffect.FreezeReversal(
            depositReversalId = currentReversal.token,
            customerId = value.customerId,
            balanceId = value.targetBalanceToken,
            createdAt = currentReversal.createdAt ?: Instant.now(),
            amount = value.amount,
            fiatEquivalent = value.fiatEquivalentAmount,
            targetWalletAddress = currentReversal.targetWalletAddress,
          )
        )
      )
    } else {
      Decision.reject("Cannot freeze reversal that does not exist or doesn't have a target wallet address")
    }
  }
}

class ReversalSanctionsHold : DepositTransition(
  from = setOf(CheckingReversalSanctions),
  to = CollectingReversalSanctionsInfo
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(state = CollectingReversalSanctionsInfo)
  )
}

class ReversalRiskApproved : DepositTransition(
  from = setOf(CheckingReversalRisk),
  to = WaitingForReversalPendingConfirmationStatus
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(state = WaitingForReversalPendingConfirmationStatus)
  )
}

class ReversalObservedInMempool : DepositTransition(
  from = setOf(WaitingForReversalPendingConfirmationStatus),
  to = WaitingForReversalConfirmedOnChainStatus
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(state = WaitingForReversalConfirmedOnChainStatus)
  )
}

class ReversalConfirmedOnChain : DepositTransition(
  from = setOf(WaitingForReversalConfirmedOnChainStatus),
  to = ReversalConfirmedComplete
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> {
    val ledgerTransactionId = value.ledgerTransactionId
    return if (ledgerTransactionId != null) {
      Decision.accept(
        value = value.copy(state = ReversalConfirmedComplete),
        effects = listOf(
          DepositEffect.ConfirmReversalTransaction(value.customerId, value.targetBalanceToken, ledgerTransactionId)
        )
      )
    } else {
      Decision.reject("Cannot confirm reversal for a deposit with no ledger transaction id")
    }
  }
}

class ReversalSanctionsInfoCollectionComplete : DepositTransition(
  from = setOf(CollectingReversalSanctionsInfo),
  to = WaitingForReversalSanctionsHeldDecision
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> =
    Decision.accept(
      value = value.copy(state = WaitingForReversalSanctionsHeldDecision)
  )
}
