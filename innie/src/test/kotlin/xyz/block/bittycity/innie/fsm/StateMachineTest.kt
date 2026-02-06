package xyz.block.bittycity.innie.fsm

import app.cash.kfsm.v2.StateMachineUtilities
import io.kotest.matchers.result.shouldBeSuccess
import org.junit.jupiter.api.Test
import xyz.block.bittycity.innie.models.CheckingDepositRisk
import xyz.block.bittycity.innie.models.CheckingDepositEligibility
import xyz.block.bittycity.innie.models.CreatingDepositTransaction
import xyz.block.bittycity.innie.models.New
import xyz.block.bittycity.innie.models.CheckingReversalRisk
import xyz.block.bittycity.innie.models.CheckingReversalSanctions
import xyz.block.bittycity.innie.models.CollectingReversalInfo
import xyz.block.bittycity.innie.models.CollectingReversalSanctionsInfo
import xyz.block.bittycity.innie.models.DepositExpiredPending
import xyz.block.bittycity.innie.models.ReversalConfirmedComplete
import xyz.block.bittycity.innie.models.ReversalSanctioned
import xyz.block.bittycity.innie.models.DepositSettled
import xyz.block.bittycity.innie.models.DepositVoided
import xyz.block.bittycity.innie.models.WaitingForDepositConfirmedOnChainStatus
import xyz.block.bittycity.innie.models.WaitingForReversalConfirmedOnChainStatus
import xyz.block.bittycity.innie.models.WaitingForReversalPendingConfirmationStatus
import xyz.block.bittycity.innie.models.WaitingForReversalSanctionsHeldDecision

class StateMachineTest {

  @Test
  fun `state machine happy path`() {
    StateMachineUtilities.verify(New) shouldBeSuccess setOf(
      New, CreatingDepositTransaction, WaitingForDepositConfirmedOnChainStatus, CheckingDepositEligibility,
      CheckingDepositRisk, DepositSettled, CollectingReversalInfo, CheckingReversalSanctions, CollectingReversalSanctionsInfo,
      WaitingForReversalSanctionsHeldDecision, ReversalSanctioned, WaitingForReversalPendingConfirmationStatus,
      WaitingForReversalConfirmedOnChainStatus, ReversalConfirmedComplete, CheckingReversalRisk, DepositExpiredPending, DepositVoided
    )
  }
}
