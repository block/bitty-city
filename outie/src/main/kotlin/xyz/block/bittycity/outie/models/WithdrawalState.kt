package xyz.block.bittycity.outie.models

import app.cash.kfsm.Invariant
import app.cash.kfsm.State
import app.cash.kfsm.StateWithInvariants
import app.cash.kfsm.invariant
import arrow.core.raise.result

/**
 * States that a withdrawal may be in. See docs/withdrawal-state-machine.md
 */
sealed class WithdrawalState() : State<WithdrawalState>() {

  val name: String =
    this.javaClass.simpleName
      .replace(regex = Regex("([a-z])([A-Z]+)"), replacement = "$1_$2")
      .replace(regex = Regex("([A-Z])([A-Z][a-z])"), replacement = "$1_$2")
      .uppercase()

  companion object {
    private val allStatesMap: Map<String, WithdrawalState> by lazy {
      val objectStates = WithdrawalState::class
        .sealedSubclasses
        .mapNotNull { c -> c.objectInstance?.let { i -> c.simpleName?.let { it to i } } }
        .flatMap { (name, instance) -> listOf(name to instance, instance.name to instance) }
        .toMap()
      objectStates + mapOf("Failed" to Failed.placeholder, Failed.placeholder.name to Failed.placeholder)
    }

    // For backwards compatibility when parsing, here's a list of deprecated states
    private val aliasedStatesMap: Map<String, WithdrawalState> by lazy {
      mapOf(
        "COLLECTING_SPEED_SELECTION" to CollectingInfo,
        "COLLECTING_USER_CONFIRMATION" to CollectingInfo,
        "PERFORMING_STEP_UP_AUTHENTICATION" to CollectingInfo,
        "RESERVING_FUNDS" to CollectingInfo,
      )
    }

    fun byName(name: String): Result<WithdrawalState> = result {
      allStatesMap[name]
        ?: aliasedStatesMap[name]
        ?: raise(IllegalStateException("Unknown withdrawal state:「$name」"))
    }

    val allStates: Set<WithdrawalState> by lazy { allStatesMap.values.toSet() }

    val countsTowardsLimits: Set<WithdrawalState> by lazy {
      allStates(from = CheckingSanctions, to = ConfirmedComplete)
    }

    fun allStates(from: WithdrawalState, to: WithdrawalState): Set<WithdrawalState> =
      allStates(listOf(from), to)

    private tailrec fun allStates(
      from: List<WithdrawalState>,
      to: WithdrawalState,
      collected: Set<WithdrawalState> = emptySet(),
    ): Set<WithdrawalState> {
      val next = from.firstOrNull()
      return when {
        next == null -> collected
        next == to -> collected + to
        next.canEventuallyTransitionTo(to) ->
          allStates(from.drop(1).plus(next.subsequentStates).distinct(), to, collected.plus(next))
        else -> allStates(from.drop(1), to, collected)
      }
    }
  }
}

data object CollectingInfo : WithdrawalState() {
  override fun transitions(): Set<WithdrawalState> = setOf(CheckingSanctions, Failed.placeholder)
}

data object CheckingRisk : WithdrawalState(), StateWithInvariants<Withdrawal> {
  override fun transitions(): Set<WithdrawalState> = setOf(CheckingTravelRule, CollectingScamWarningDecision, Failed.placeholder)
  override fun invariants(): List<Invariant<Withdrawal>> = listOf(
    invariant("Target wallet address must be set") { withdrawal ->
      withdrawal.targetWalletAddress != null
    }
  )
}

data object CollectingScamWarningDecision : WithdrawalState() {
  override fun transitions(): Set<WithdrawalState> = setOf(CheckingTravelRule, Failed.placeholder)
}

data object CheckingSanctions : WithdrawalState() {
  override fun transitions(): Set<WithdrawalState> = setOf(CheckingRisk, CollectingSanctionsInfo, Failed.placeholder)
}

data object Sanctioned : WithdrawalState() {
  override fun transitions(): Set<WithdrawalState> = emptySet()
}

data object CollectingSanctionsInfo : WithdrawalState() {
  override fun transitions(): Set<WithdrawalState> = setOf(
    CheckingEligibility, Sanctioned, WaitingForSanctionsHeldDecision, Failed.placeholder
  )
}

data object WaitingForSanctionsHeldDecision : WithdrawalState() {
  override fun transitions(): Set<WithdrawalState> = setOf(CheckingEligibility, Sanctioned, Failed.placeholder)
}

data object CheckingTravelRule : WithdrawalState() {
  override fun transitions(): Set<WithdrawalState> = setOf(CheckingEligibility, CollectingSelfAttestation, Failed.placeholder)
}

data object CollectingSelfAttestation : WithdrawalState() {
  override fun transitions(): Set<WithdrawalState> = setOf(CheckingEligibility, Failed.placeholder)
}

data object CheckingEligibility : WithdrawalState() {
  override fun transitions(): Set<WithdrawalState> = setOf(CheckingLimits, Failed.placeholder)
}

data object CheckingLimits : WithdrawalState() {
  override fun transitions(): Set<WithdrawalState> = setOf(SubmittingOnChain, Failed.placeholder)
}

data object SubmittingOnChain : WithdrawalState() {
  override fun transitions(): Set<WithdrawalState> = setOf(WaitingForPendingConfirmationStatus, Failed.placeholder)
}

data object WaitingForPendingConfirmationStatus : WithdrawalState() {
  override fun transitions(): Set<WithdrawalState> = setOf(WaitingForConfirmedOnChainStatus, ConfirmedComplete, Failed.placeholder)
}

data object WaitingForConfirmedOnChainStatus : WithdrawalState() {
  override fun transitions(): Set<WithdrawalState> = setOf(ConfirmedComplete, Failed.placeholder)
}

data object ConfirmedComplete : WithdrawalState() {
  override fun transitions(): Set<WithdrawalState> = emptySet()
}
data class Failed(
  val failureReason: FailureReason
) : WithdrawalState() {
  override fun transitions(): Set<WithdrawalState> = emptySet()

  companion object {
    val placeholder = Failed(FailureReason.UNKNOWN)
  }
}
