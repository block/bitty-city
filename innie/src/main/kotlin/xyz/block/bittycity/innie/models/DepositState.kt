package xyz.block.bittycity.innie.models

import app.cash.kfsm.Invariant
import app.cash.kfsm.State
import app.cash.kfsm.invariant
import arrow.core.raise.result
import xyz.block.bittycity.common.models.BitcoinDisplayUnits
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.ProcessingState.Complete
import xyz.block.domainapi.ProcessingState.Waiting

sealed class DepositState(
  to: () -> Set<DepositState> = { emptySet() },
  invariants: List<Invariant<DepositToken, Deposit, DepositState>> = emptyList(),
  val processingState: (Deposit, BitcoinDisplayUnits?) ->
  ProcessingState<Deposit, RequirementId> = { deposit, displayPref -> Complete(deposit) }
) : State<DepositToken, Deposit, DepositState>(to, invariants) {

  val name: String =
    this.javaClass.simpleName
      .replace(regex = Regex("([a-z])([A-Z]+)"), replacement = "$1_$2")
      .replace(regex = Regex("([A-Z])([A-Z][a-z])"), replacement = "$1_$2")
      .uppercase()

  fun isAtOrUpstream(other: DepositState) = this == other ||
          this.canEventuallyTransitionTo(other)

  fun isAtOrDownstream(other: DepositState) = this == other ||
          other.canEventuallyTransitionTo(this)

  fun byName(name: String): Result<DepositState> = result {
    allStatesMap[name]
      ?: raise(IllegalStateException("Unknown deposit state: $name"))
  }

  val allStates: Set<DepositState> by lazy { allStatesMap.values.toSet() }

  companion object {
    private val allStatesMap: Map<String, DepositState> by lazy {
      DepositState::class
        .sealedSubclasses
        .mapNotNull { c -> c.objectInstance?.let { i -> c.simpleName?.let { it to i } } }
        .flatMap { (name, instance) -> listOf(name to instance, instance.name to instance) }
        .toMap()
    }
  }
}

data object WaitingForDepositConfirmedOnChainStatus : DepositState(
  to = { setOf(CheckingEligibility, ExpiredPending, Voided) }
)

data object ExpiredPending : DepositState(
  to = { setOf(CheckingEligibility, Voided) }
)

data object Voided : DepositState()

data object CheckingEligibility : DepositState(
  to = { setOf(CheckingDepositRisk, WaitingForReversal) },
)

data object CheckingDepositRisk : DepositState(
  to = { setOf(Settled, WaitingForReversal) }
)

data object Settled : DepositState()

data object WaitingForReversal : DepositState(
  to = { setOf(CollectingInfo) },
  invariants =  listOf(
    invariant("failure reason must be set") { it.failureReason != null },
  ),
  processingState = { deposit, displayPref -> Waiting(deposit) }
)

data object CollectingInfo : DepositState(
  to = { setOf(CheckingSanctions, WaitingForReversal) }
)

data object CheckingSanctions : DepositState(
  to = { setOf(CollectingSanctionsInfo, CheckingReversalRisk, WaitingForReversal) }
)

data object CheckingReversalRisk : DepositState(
  to = { setOf(WaitingForReversalPendingConfirmationStatus, WaitingForReversal) },
  invariants = listOf(
    invariant("target address must be set") { it.reversals.isNotEmpty() && it.reversals.last().targetWalletAddress != null }
  )
)

data object CollectingSanctionsInfo : DepositState(
  to = {
    setOf(WaitingForSanctionsHeldDecision, Sanctioned, WaitingForReversalPendingConfirmationStatus, WaitingForReversal)
  }
)

data object WaitingForSanctionsHeldDecision : DepositState(
  to = { setOf(Sanctioned, WaitingForReversalPendingConfirmationStatus, WaitingForReversal) }
)

data object Sanctioned : DepositState()

data object WaitingForReversalPendingConfirmationStatus : DepositState(
  to = { setOf(WaitingForReversalConfirmedOnChainStatus, WaitingForReversal) }
)

data object WaitingForReversalConfirmedOnChainStatus : DepositState(
  to = { setOf(ReversalConfirmedComplete, WaitingForReversal) },
  processingState = { deposit, displayPref -> Waiting(deposit) }
)

data object ReversalConfirmedComplete : DepositState()
