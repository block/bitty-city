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
  override fun transitions(): Set<DepositState> = setOf(AwaitingDepositConfirmation)
}

data object AwaitingDepositConfirmation : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(CheckingEligibility, Evicted, Voided)
}

data object Evicted : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(CheckingEligibility, Voided)
}

data object Voided : DepositState() {
  override fun transitions(): Set<DepositState> = emptySet()
}

data object CheckingEligibility : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(CheckingDepositRisk, PendingReversal)
}

data object CheckingDepositRisk : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(Settled, PendingReversal)
}

data object Settled : DepositState() {
  override fun transitions(): Set<DepositState> = emptySet()
}

data object PendingReversal : DepositState(), StateWithInvariants<Deposit> {
  override fun transitions(): Set<DepositState> = setOf(CheckingSanctions)

  override fun invariants(): List<Invariant<Deposit>> = listOf(
    invariant("failure reason must be set") { deposit ->
      deposit.failureReason != null
    }
  )
}

data object CheckingSanctions : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(CheckingReversalRisk, CollectingSanctionsInfo, PendingReversal)
}

data object CheckingReversalRisk : DepositState(), StateWithInvariants<Deposit> {
  override fun transitions(): Set<DepositState> = setOf(AwaitingReversalPendingConfirmation, PendingReversal)

  override fun invariants(): List<Invariant<Deposit>> = listOf(
    invariant("target address must be set") { deposit ->
      deposit.reversals.isNotEmpty() && deposit.reversals.last().targetWalletAddress != null
    }
  )
}

data object CollectingSanctionsInfo : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(
    AwaitingSanctionsDecision,
    Sanctioned, // These are possible from this state if the user abandons the sanctions collection screen
    AwaitingReversalPendingConfirmation,
    PendingReversal
  )
}

data object AwaitingSanctionsDecision : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(
    CheckingReversalRisk, Sanctioned, PendingReversal
  )
}

data object Sanctioned : DepositState() {
  override fun transitions(): Set<DepositState> = emptySet()
}

data object AwaitingReversalPendingConfirmation : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(AwaitingReversalConfirmation, PendingReversal)
}

data object AwaitingReversalConfirmation : DepositState() {
  override fun transitions(): Set<DepositState> = setOf(Reversed, PendingReversal)
}

data object Reversed : DepositState() {
  override fun transitions(): Set<DepositState> = emptySet()
}
