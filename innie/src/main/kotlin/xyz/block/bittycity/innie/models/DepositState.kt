package xyz.block.bittycity.innie.models

import app.cash.kfsm.v2.Invariant
import app.cash.kfsm.v2.State
import app.cash.kfsm.v2.StateWithInvariants
import app.cash.kfsm.v2.invariant
import arrow.core.raise.result

sealed class DepositState() : State<DepositState>() {

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

data object New : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(CreatingDepositTransaction)
}

data object CreatingDepositTransaction: DepositState() {
  override fun transitions(): Set<DepositState> = setOf(WaitingForDepositConfirmedOnChainStatus)
}

data object WaitingForDepositConfirmedOnChainStatus : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(CheckingDepositEligibility, DepositExpiredPending, DepositVoided)
}

data object DepositExpiredPending : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(CheckingDepositEligibility, DepositVoided)
}

data object DepositVoided : DepositState() {
  override fun transitions(): Set<DepositState> = emptySet()
}

data object CheckingDepositEligibility : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(CheckingDepositRisk, CollectingReversalInfo)
}

data object CheckingDepositRisk : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(DepositSettled, CollectingReversalInfo)
}

data object DepositSettled : DepositState() {
  override fun transitions(): Set<DepositState> = emptySet()
}

data object CollectingReversalInfo : DepositState(), StateWithInvariants<Deposit> {
  override fun transitions(): Set<DepositState> = setOf(CheckingReversalSanctions)

  override fun invariants(): List<Invariant<Deposit>> = listOf(
    invariant("failure reason must be set") { deposit ->
      deposit.failureReason != null
    }
  )
}

data object CheckingReversalSanctions : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(CheckingReversalRisk, CollectingReversalSanctionsInfo, CollectingReversalInfo)
}

data object CheckingReversalRisk : DepositState(), StateWithInvariants<Deposit> {
  override fun transitions(): Set<DepositState> = setOf(WaitingForReversalPendingConfirmationStatus, CollectingReversalInfo)

  override fun invariants(): List<Invariant<Deposit>> = listOf(
    invariant("target address must be set") { deposit ->
      deposit.reversals.isNotEmpty() && deposit.reversals.last().targetWalletAddress != null
    }
  )
}

data object CollectingReversalSanctionsInfo : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(
    WaitingForReversalSanctionsHeldDecision,
    ReversalSanctioned, // These are possible from this state if the user abandons the sanctions collection screen
    WaitingForReversalPendingConfirmationStatus,
    CollectingReversalInfo
  )
}

data object WaitingForReversalSanctionsHeldDecision : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(
    CheckingReversalRisk, ReversalSanctioned, CollectingReversalInfo
  )
}

data object ReversalSanctioned : DepositState() {
  override fun transitions(): Set<DepositState> = emptySet()
}

data object WaitingForReversalPendingConfirmationStatus : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(WaitingForReversalConfirmedOnChainStatus, CollectingReversalInfo)
}

data object WaitingForReversalConfirmedOnChainStatus : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(ReversalConfirmedComplete, CollectingReversalInfo)
}

data object ReversalConfirmedComplete : DepositState() {
  override fun transitions(): Set<DepositState> = emptySet()
}
