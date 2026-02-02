package xyz.block.bittycity.outie.fsm

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import xyz.block.bittycity.outie.models.CheckingEligibility
import xyz.block.bittycity.outie.models.CheckingRisk
import xyz.block.bittycity.outie.models.CheckingSanctions
import xyz.block.bittycity.outie.models.CheckingTravelRule
import xyz.block.bittycity.outie.models.CollectingSanctionsInfo
import xyz.block.bittycity.outie.models.CollectingScamWarningDecision
import xyz.block.bittycity.outie.models.CollectingSelfAttestation
import xyz.block.bittycity.outie.models.ConfirmedComplete
import xyz.block.bittycity.outie.models.CheckingLimits
import xyz.block.bittycity.outie.models.Sanctioned
import xyz.block.bittycity.outie.models.SubmittingOnChain
import xyz.block.bittycity.outie.models.WaitingForConfirmedOnChainStatus
import xyz.block.bittycity.outie.models.WaitingForPendingConfirmationStatus
import xyz.block.bittycity.outie.models.WaitingForSanctionsHeldDecision
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalState.Companion.countsTowardsLimits
import xyz.block.bittycity.outie.testing.Arbitrary.withdrawalState

class WithdrawalStateTest {

  @Test
  fun `Anything counting towards limits can eventually be confirmed`() {
    runTest {
      checkAll(Arb.element(countsTowardsLimits)) {
        withClue("Failed state: $it") {
          (it == ConfirmedComplete || it.canEventuallyTransitionTo(ConfirmedComplete)) shouldBe true
        }
      }
    }
  }

  @Test
  fun `allStates finds all states`() {
    WithdrawalState.allStates(from = CheckingSanctions, to = ConfirmedComplete) shouldContainExactly
      setOf(
        CheckingEligibility,
        CheckingRisk,
        CheckingSanctions,
        CheckingTravelRule,
        CollectingSanctionsInfo,
        CollectingScamWarningDecision,
        CollectingSelfAttestation,
        ConfirmedComplete,
        CheckingLimits,
        SubmittingOnChain,
        WaitingForConfirmedOnChainStatus,
        WaitingForPendingConfirmationStatus,
        WaitingForSanctionsHeldDecision,
      )
  }

  @Test
  fun `allStates finds no states going backwards`() {
    WithdrawalState.allStates(from = ConfirmedComplete, to = CheckingSanctions).shouldBeEmpty()
  }

  @Test
  fun `allStates finds no states between disjointed states`() {
    WithdrawalState.allStates(from = Sanctioned, to = CheckingLimits).shouldBeEmpty()
  }

  @Test
  fun `allStates finds self`() {
    runTest {
      checkAll(withdrawalState) {
        WithdrawalState.allStates(it, it) shouldBe setOf(it)
      }
    }
  }

  @Test
  fun `allStates never errors`() {
    runTest {
      checkAll(withdrawalState, withdrawalState) { from, to ->
        WithdrawalState.allStates(from, to).shouldBeInstanceOf<Set<WithdrawalState>>()
      }
    }
  }
}
