package xyz.block.bittycity.innie.fsm

import app.cash.kfsm.v2.StateMachineUtilities
import io.kotest.matchers.result.shouldBeSuccess
import org.junit.jupiter.api.Test
import xyz.block.bittycity.innie.models.CheckingDepositRisk
import xyz.block.bittycity.innie.models.CheckingEligibility
import xyz.block.bittycity.innie.models.CreatingDepositTransaction
import xyz.block.bittycity.innie.models.New
import xyz.block.bittycity.innie.models.CheckingReversalRisk
import xyz.block.bittycity.innie.models.CheckingSanctions
import xyz.block.bittycity.innie.models.PendingReversal
import xyz.block.bittycity.innie.models.CollectingSanctionsInfo
import xyz.block.bittycity.innie.models.Evicted
import xyz.block.bittycity.innie.models.Reversed
import xyz.block.bittycity.innie.models.Sanctioned
import xyz.block.bittycity.innie.models.Settled
import xyz.block.bittycity.innie.models.Voided
import xyz.block.bittycity.innie.models.AwaitingDepositConfirmation
import xyz.block.bittycity.innie.models.AwaitingReversalConfirmation
import xyz.block.bittycity.innie.models.AwaitingReversalPendingConfirmation
import xyz.block.bittycity.innie.models.AwaitingSanctionsDecision

class StateMachineTest {

  @Test
  fun `state machine happy path`() {
    StateMachineUtilities.verify(New) shouldBeSuccess setOf(
      New, CreatingDepositTransaction, AwaitingDepositConfirmation, CheckingEligibility,
      CheckingDepositRisk, Settled, PendingReversal, CheckingSanctions, CollectingSanctionsInfo,
      AwaitingSanctionsDecision, Sanctioned, AwaitingReversalPendingConfirmation,
      AwaitingReversalConfirmation, Reversed, CheckingReversalRisk, Evicted, Voided
    )
  }
}
