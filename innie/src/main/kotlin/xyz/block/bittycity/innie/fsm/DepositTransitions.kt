package xyz.block.bittycity.innie.fsm

import app.cash.kfsm.v2.Decision
import app.cash.kfsm.v2.Transition
import xyz.block.bittycity.common.models.LedgerTransactionId
import xyz.block.bittycity.innie.models.CheckingDepositRisk
import xyz.block.bittycity.innie.models.CheckingEligibility
import xyz.block.bittycity.innie.models.CheckingReversalRisk
import xyz.block.bittycity.innie.models.CheckingSanctions
import xyz.block.bittycity.innie.models.PendingReversal
import xyz.block.bittycity.innie.models.CollectingSanctionsInfo
import xyz.block.bittycity.innie.models.CreatingDepositTransaction
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.Evicted
import xyz.block.bittycity.innie.models.DepositFailureReason
import xyz.block.bittycity.innie.models.DepositReversalFailureReason
import xyz.block.bittycity.innie.models.Settled
import xyz.block.bittycity.innie.models.Voided
import xyz.block.bittycity.innie.models.AwaitingDepositConfirmation
import xyz.block.bittycity.innie.models.New
import xyz.block.bittycity.innie.models.Reversed
import xyz.block.bittycity.innie.models.Sanctioned
import xyz.block.bittycity.innie.models.AwaitingReversalConfirmation
import xyz.block.bittycity.innie.models.AwaitingReversalPendingConfirmation
import xyz.block.bittycity.innie.models.AwaitingSanctionsDecision
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
        effects = buildList {
          addAll(decision.effects)
          add(
            DepositEffect.PublishStateTransitionMetric(
              from = value.state,
              to = decision.value.state,
              failureReason = decision.value.failureReason
            )
          )
          if (decision.value.state == Settled) {
            add(DepositEffect.PublishDepositSuccessAmountMetric(decision.value))
          }
        }
      )
      is Decision.Reject -> decision
    }

  protected abstract fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect>
}

class DepositObservedInMempool() : DepositTransition(
  from = New,
  to = AwaitingDepositConfirmation
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(state = AwaitingDepositConfirmation),
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
  to = AwaitingDepositConfirmation
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(
      state = AwaitingDepositConfirmation,
      ledgerTransactionId = ledgerTransactionId
    )
  )
}

/**
 * Deposits that remain pending on-chain for too long are considered expired locally. Unlike Voided, these may later
 * transition to ConfirmedOnBlockchain if a late confirmation arrives.
 */
class DepositExpiredLocally() : DepositTransition(
  from = AwaitingDepositConfirmation,
  to = Evicted
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(state = Evicted)
  )
}

class Voided() : DepositTransition(
  from = setOf(AwaitingDepositConfirmation, Evicted),
  to = Voided
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(state = Voided),
    effects = listOf(
      DepositEffect.VoidDepositTransaction(value.customerId, value.targetBalanceToken, value.ledgerTransactionId)
    )
  )
}

class DepositConfirmedOnChain() : DepositTransition(
  from = setOf(AwaitingDepositConfirmation, Evicted),
  to = CheckingEligibility
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(state = CheckingEligibility),
    effects = listOf(
      DepositEffect.RequestDepositEligibilityCheck(value.customerId)
    )
  )
}

class IsEligibleForDeposit() : DepositTransition(
  from = setOf(CheckingEligibility),
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
  from = setOf(CheckingEligibility),
  to = CheckingDepositRisk
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(state = CheckingDepositRisk)
  )
}

class DepositFailed(
  val reason: DepositFailureReason
) : DepositTransition(
  from = setOf(CheckingEligibility, CheckingDepositRisk),
  to = PendingReversal
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(failureReason = reason, state = PendingReversal),
    effects = listOf(DepositEffect.PublishFailureReasonMetric(reason))
  )
}

class DepositRiskApproved() : DepositTransition(
  from = setOf(CheckingDepositRisk),
  to = Settled
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> {
    val transactionId = value.ledgerTransactionId

    return if (transactionId != null) {
      Decision.accept(
        value = value.copy(state = Settled),
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
    CheckingSanctions,
    CheckingReversalRisk,
    AwaitingReversalPendingConfirmation,
    AwaitingReversalConfirmation,
    AwaitingSanctionsDecision
  ),
  to = PendingReversal
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> {
    val currentReversal = value.currentReversal
    return if (currentReversal != null) {
      Decision.accept(
        value = value.updateCurrentReversal {
          it.copy(failureReason = reason)
        }.getOrNull()!!
          .copy(state = PendingReversal),
        effects = listOf(DepositEffect.PublishReversalFailureReasonMetric(reason))
      )
    } else  {
      Decision.reject("Cannot fail a reversal that doesn't exist")
    }
  }
}

class ReversalInfoCollectionComplete : DepositTransition(
  from = setOf(PendingReversal),
  to = CheckingSanctions
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> {
    val targetWalletAddress = value.reversals.lastOrNull()?.targetWalletAddress
    val reversalId = value.reversals.lastOrNull()?.token
    return if (targetWalletAddress != null && reversalId != null) {
      Decision.accept(
        value = value.copy(state = CheckingSanctions),
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
  from = setOf(CheckingSanctions, AwaitingSanctionsDecision),
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
  from = setOf(AwaitingSanctionsDecision),
  to = Sanctioned
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> {
    val currentReversal = value.currentReversal
    val targetWalletAddress = currentReversal?.targetWalletAddress
    val ledgerTransactionId = currentReversal?.ledgerTransactionId
    return if (currentReversal != null && targetWalletAddress != null && ledgerTransactionId != null) {
      Decision.accept(
        value = value.copy(state = Sanctioned),
        effects = listOf(
          DepositEffect.FreezeReversal(
            depositReversalId = currentReversal.token,
            customerId = value.customerId,
            balanceId = value.targetBalanceToken,
            createdAt = currentReversal.createdAt ?: Instant.now(),
            amount = value.amount,
            fiatEquivalent = value.fiatEquivalentAmount,
            targetWalletAddress = currentReversal.targetWalletAddress,
            ledgerTransactionId = ledgerTransactionId
          )
        )
      )
    } else {
      Decision.reject(
        "Cannot freeze reversal that does not exist or is missing target wallet address or ledger transaction id"
      )
    }
  }
}

class ReversalSanctionsHold : DepositTransition(
  from = setOf(CheckingSanctions),
  to = CollectingSanctionsInfo
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(state = CollectingSanctionsInfo)
  )
}

class ReversalRiskApproved : DepositTransition(
  from = setOf(CheckingReversalRisk),
  to = AwaitingReversalPendingConfirmation
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(state = AwaitingReversalPendingConfirmation)
  )
}

class ReversalObservedInMempool : DepositTransition(
  from = setOf(AwaitingReversalPendingConfirmation),
  to = AwaitingReversalConfirmation
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> = Decision.accept(
    value = value.copy(state = AwaitingReversalConfirmation)
  )
}

class ReversalConfirmedOnChain : DepositTransition(
  from = setOf(AwaitingReversalConfirmation),
  to = Reversed
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> {
    val ledgerTransactionId = value.ledgerTransactionId
    return if (ledgerTransactionId != null) {
      Decision.accept(
        value = value.copy(state = Reversed),
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
  from = setOf(CollectingSanctionsInfo),
  to = AwaitingSanctionsDecision
) {
  override fun transitionDecision(value: Deposit): Decision<Deposit, DepositState, DepositEffect> =
    Decision.accept(
      value = value.copy(state = AwaitingSanctionsDecision)
  )
}
