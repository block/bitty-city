package xyz.block.bittycity.outie.models

import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WithdrawalStateTest {
  @Test
  fun `byName should return state for simple class name`() {
    WithdrawalState.byName("CollectingInfo").getOrThrow() shouldBe CollectingInfo
  }

  @Test
  fun `byName should return state for UPPER_SNAKE_CASE name`() {
    WithdrawalState.byName("COLLECTING_INFO").getOrThrow() shouldBe CollectingInfo
  }

  @Test
  fun `byName should return state for all current states`() {
    WithdrawalState.byName("CHECKING_SANCTIONS").getOrThrow() shouldBe CheckingSanctions
    WithdrawalState.byName("CHECKING_RISK").getOrThrow() shouldBe CheckingRisk
    WithdrawalState.byName("COLLECTING_SCAM_WARNING_DECISION").getOrThrow() shouldBe CollectingScamWarningDecision
    WithdrawalState.byName("SANCTIONED").getOrThrow() shouldBe Sanctioned
    WithdrawalState.byName("COLLECTING_SANCTIONS_INFO").getOrThrow() shouldBe CollectingSanctionsInfo
    WithdrawalState.byName("WAITING_FOR_SANCTIONS_HELD_DECISION").getOrThrow() shouldBe WaitingForSanctionsHeldDecision
    WithdrawalState.byName("CHECKING_TRAVEL_RULE").getOrThrow() shouldBe CheckingTravelRule
    WithdrawalState.byName("COLLECTING_SELF_ATTESTATION").getOrThrow() shouldBe CollectingSelfAttestation
    WithdrawalState.byName("CHECKING_ELIGIBILITY").getOrThrow() shouldBe CheckingEligibility
    WithdrawalState.byName("HOLDING_SUBMISSION").getOrThrow() shouldBe HoldingSubmission
    WithdrawalState.byName("SUBMITTING_ON_CHAIN").getOrThrow() shouldBe SubmittingOnChain
    WithdrawalState.byName("WAITING_FOR_PENDING_CONFIRMATION_STATUS").getOrThrow() shouldBe WaitingForPendingConfirmationStatus
    WithdrawalState.byName("WAITING_FOR_CONFIRMED_ON_CHAIN_STATUS").getOrThrow() shouldBe WaitingForConfirmedOnChainStatus
    WithdrawalState.byName("CONFIRMED_COMPLETE").getOrThrow() shouldBe ConfirmedComplete
    WithdrawalState.byName("FAILED").getOrThrow() shouldBe Failed
  }

  @Test
  fun `byName should return CollectingInfo for deprecated RESERVING_FUNDS state`() {
    WithdrawalState.byName("RESERVING_FUNDS").getOrThrow() shouldBe CollectingInfo
  }

  @Test
  fun `byName should return CollectingInfo for deprecated COLLECTING_SPEED_SELECTION state`() {
    WithdrawalState.byName("COLLECTING_SPEED_SELECTION").getOrThrow() shouldBe CollectingInfo
  }

  @Test
  fun `byName should return CollectingInfo for deprecated COLLECTING_USER_CONFIRMATION state`() {
    WithdrawalState.byName("COLLECTING_USER_CONFIRMATION").getOrThrow() shouldBe CollectingInfo
  }

  @Test
  fun `byName should return CollectingInfo for deprecated PERFORMING_STEP_UP_AUTHENTICATION state`() {
    WithdrawalState.byName("PERFORMING_STEP_UP_AUTHENTICATION").getOrThrow() shouldBe CollectingInfo
  }

  @Test
  fun `byName should fail for unknown state name`() {
    WithdrawalState.byName("INVALID_STATE").shouldBeFailure<IllegalStateException>()
  }
}
