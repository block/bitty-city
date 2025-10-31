package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.StateMachineUtilities
import xyz.block.bittycity.outie.models.CheckingEligibility
import xyz.block.bittycity.outie.models.CheckingRisk
import xyz.block.bittycity.outie.models.CheckingSanctions
import xyz.block.bittycity.outie.models.CheckingTravelRule
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.outie.models.CollectingSanctionsInfo
import xyz.block.bittycity.outie.models.CollectingScamWarningDecision
import xyz.block.bittycity.outie.models.CollectingSelfAttestation
import xyz.block.bittycity.outie.models.ConfirmedComplete
import xyz.block.bittycity.outie.models.Failed
import xyz.block.bittycity.outie.models.HoldingSubmission
import xyz.block.bittycity.outie.models.Sanctioned
import xyz.block.bittycity.outie.models.SubmittingOnChain
import xyz.block.bittycity.outie.models.WaitingForConfirmedOnChainStatus
import xyz.block.bittycity.outie.models.WaitingForPendingConfirmationStatus
import xyz.block.bittycity.outie.models.WaitingForSanctionsHeldDecision
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import io.kotest.matchers.result.shouldBeSuccess
import org.junit.jupiter.api.Test

class StateMachineTest : BittyCityTestCase() {

  @Test
  fun `state machine happy path`() = runTest {
    StateMachineUtilities.verify(CollectingInfo) shouldBeSuccess setOf(
      CollectingInfo, CheckingRisk, CheckingSanctions, CheckingTravelRule,
      HoldingSubmission, SubmittingOnChain, WaitingForPendingConfirmationStatus,
      WaitingForConfirmedOnChainStatus, ConfirmedComplete, Failed, CollectingSelfAttestation,
      WaitingForSanctionsHeldDecision, CollectingScamWarningDecision, CollectingSanctionsInfo,
      Sanctioned, CheckingEligibility
    )
  }
}
