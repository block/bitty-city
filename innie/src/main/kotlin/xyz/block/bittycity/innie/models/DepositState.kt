package xyz.block.bittycity.innie.models

import app.cash.kfsm.Invariant
import app.cash.kfsm.State
import arrow.core.raise.result
import xyz.block.bittycity.common.models.BitcoinDisplayUnits
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.ProcessingState.Complete

class DepositState(
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