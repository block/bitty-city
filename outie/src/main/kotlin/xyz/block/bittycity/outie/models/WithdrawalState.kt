package xyz.block.bittycity.outie.models

import app.cash.kfsm.Invariant
import app.cash.kfsm.State
import app.cash.kfsm.invariant
import arrow.core.raise.result
import xyz.block.bittycity.common.models.BitcoinDisplayUnits
import xyz.block.domainapi.DomainApi
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.ProcessingState.Complete
import xyz.block.domainapi.ProcessingState.Waiting

/**
 * States that a withdrawal may be in. See docs/withdrawal-state-machine.md
 */
sealed class WithdrawalState(
  to: () -> Set<WithdrawalState> = { emptySet() },
  invariants: List<Invariant<WithdrawalToken, Withdrawal, WithdrawalState>> = emptyList(),
  val processingState: (Withdrawal, BitcoinDisplayUnits?) ->
  ProcessingState<Withdrawal, RequirementId> = { withdrawal, displayPref -> Complete(withdrawal) }
) : State<WithdrawalToken, Withdrawal, WithdrawalState>(to, invariants) {

  val name: String =
    this.javaClass.simpleName
      .replace(regex = Regex("([a-z])([A-Z]+)"), replacement = "$1_$2")
      .replace(regex = Regex("([A-Z])([A-Z][a-z])"), replacement = "$1_$2")
      .uppercase()

  fun isAtOrUpstream(other: WithdrawalState) = this == other ||
    this.canEventuallyTransitionTo(other)

  fun isAtOrDownstream(other: WithdrawalState) = this == other ||
    other.canEventuallyTransitionTo(this)

  companion object {
    private val allStatesMap: Map<String, WithdrawalState> by lazy {
      WithdrawalState::class
        .sealedSubclasses
        .mapNotNull { c -> c.objectInstance?.let { i -> c.simpleName?.let { it to i } } }
        .flatMap { (name, instance) -> listOf(name to instance, instance.name to instance) }
        .toMap()
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

    val nonSuccessfulTerminalStates: Set<WithdrawalState> by lazy {
      allStates.filter { it != ConfirmedComplete && it.reachableStates.isEmpty() }.toSet()
    }

    val submittedToChain: Set<WithdrawalState> by lazy {
      HoldingSubmission.reachableStates.minus(Failed)
        .plus(HoldingSubmission)
    }

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

data object CollectingInfo : WithdrawalState(
  { setOf(CheckingSanctions, Failed) }
)

data object CheckingRisk : WithdrawalState(
  to = { setOf(CheckingTravelRule, CollectingScamWarningDecision, Failed) },
  invariants = listOf(
    invariant("target address must be set") { it.targetWalletAddress != null },
  )
)

data object CollectingScamWarningDecision : WithdrawalState(
  { setOf(CheckingTravelRule, Failed) }
)

data object CheckingSanctions : WithdrawalState(
  { setOf(CheckingRisk, CollectingSanctionsInfo, Failed) }
)

data object Sanctioned : WithdrawalState()

data object CollectingSanctionsInfo : WithdrawalState(
  { setOf(CheckingEligibility, Sanctioned, WaitingForSanctionsHeldDecision, Failed) }
)

data object WaitingForSanctionsHeldDecision : WithdrawalState(
  to = { setOf(CheckingEligibility, Sanctioned, Failed) },
  processingState = { withdrawal, displayPref -> Waiting(withdrawal) }
)

data object CheckingTravelRule : WithdrawalState(
  { setOf(CheckingEligibility, CollectingSelfAttestation, Failed) }
)

data object CollectingSelfAttestation : WithdrawalState(
  { setOf(CheckingEligibility, Failed) }
)

data object CheckingEligibility : WithdrawalState({ setOf(HoldingSubmission, Failed) })

data object HoldingSubmission : WithdrawalState({ setOf(SubmittingOnChain, Failed) })

data object SubmittingOnChain : WithdrawalState({
  setOf(WaitingForPendingConfirmationStatus, Failed)
})

data object WaitingForPendingConfirmationStatus : WithdrawalState(
  to = { setOf(WaitingForConfirmedOnChainStatus, ConfirmedComplete, Failed) },
  processingState = { withdrawal, displayPref ->
    ProcessingState.UserInteractions(
      hurdles = listOf(
        WithdrawalNotification.SubmittedOnChainNotification(
          withdrawal.amount!!,
          displayPref!!,
          withdrawal.fiatEquivalentAmount!!,
          withdrawal.selectedSpeed?.approximateWaitTime!!,
          withdrawal.targetWalletAddress!!
        )
      ),
      nextEndpoint = DomainApi.Endpoint.SECURE_EXECUTE
    )
  }
)

data object WaitingForConfirmedOnChainStatus : WithdrawalState(
  to = { setOf(ConfirmedComplete, Failed) },
  processingState = { withdrawal, displayPref -> Waiting(withdrawal) }
)

data object ConfirmedComplete : WithdrawalState()
data object Failed : WithdrawalState(
  invariants = listOf(
    invariant("Failure reason must be set") { it.failureReason != null },
  )
)
