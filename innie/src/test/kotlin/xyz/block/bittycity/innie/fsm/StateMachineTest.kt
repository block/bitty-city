package xyz.block.bittycity.innie.fsm

import app.cash.kfsm.StateMachineUtilities
import io.kotest.matchers.result.shouldBeSuccess
import org.junit.jupiter.api.Test
import xyz.block.bittycity.innie.models.CheckingDepositRisk
import xyz.block.bittycity.innie.models.CheckingEligibility
import xyz.block.bittycity.innie.models.CheckingReversalRisk
import xyz.block.bittycity.innie.models.CheckingSanctions
import xyz.block.bittycity.innie.models.CollectingInfo
import xyz.block.bittycity.innie.models.CollectingSanctionsInfo
import xyz.block.bittycity.innie.models.ExpiredPending
import xyz.block.bittycity.innie.models.ReversalConfirmedComplete
import xyz.block.bittycity.innie.models.Sanctioned
import xyz.block.bittycity.innie.models.Settled
import xyz.block.bittycity.innie.models.Voided
import xyz.block.bittycity.innie.models.WaitingForDepositConfirmedOnChainStatus
import xyz.block.bittycity.innie.models.WaitingForReversal
import xyz.block.bittycity.innie.models.WaitingForReversalConfirmedOnChainStatus
import xyz.block.bittycity.innie.models.WaitingForReversalPendingConfirmationStatus
import xyz.block.bittycity.innie.models.WaitingForSanctionsHeldDecision

class StateMachineTest {

  @Test
  fun `state machine happy path`() {
    StateMachineUtilities.verify(WaitingForDepositConfirmedOnChainStatus) shouldBeSuccess setOf(
      WaitingForDepositConfirmedOnChainStatus, CheckingEligibility,
      CheckingDepositRisk, Settled, WaitingForReversal, CollectingInfo, CheckingSanctions, CollectingSanctionsInfo,
      WaitingForSanctionsHeldDecision, Sanctioned, WaitingForReversalPendingConfirmationStatus,
      WaitingForReversalConfirmedOnChainStatus, ReversalConfirmedComplete, CheckingReversalRisk, ExpiredPending, Voided
    )
  }
}
