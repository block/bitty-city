package xyz.block.bittycity.innie.fsm

import app.cash.kfsm.v2.Decision
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import org.junit.jupiter.api.Test
import xyz.block.bittycity.innie.models.CheckingDepositRisk
import xyz.block.bittycity.innie.models.CheckingSanctions
import xyz.block.bittycity.innie.models.AwaitingSanctionsDecision
import xyz.block.bittycity.innie.models.DepositFailureReason.RISK_BLOCKED
import xyz.block.bittycity.innie.models.DepositReversal
import xyz.block.bittycity.innie.models.DepositReversalFailureReason.SANCTIONS_FAILED
import xyz.block.bittycity.innie.models.PendingReversal
import xyz.block.bittycity.innie.models.Sanctioned
import xyz.block.bittycity.innie.models.Settled
import xyz.block.bittycity.innie.testing.Arbitrary
import xyz.block.bittycity.innie.testing.Arbitrary.amount
import xyz.block.bittycity.innie.testing.Arbitrary.balanceId
import xyz.block.bittycity.innie.testing.Arbitrary.customerId
import xyz.block.bittycity.innie.testing.Arbitrary.exchangeRate
import xyz.block.bittycity.innie.testing.Arbitrary.ledgerTransactionId
import xyz.block.bittycity.innie.testing.Arbitrary.outputIndex
import xyz.block.bittycity.innie.testing.Arbitrary.stringToken
import xyz.block.bittycity.innie.testing.Arbitrary.walletAddress
import xyz.block.bittycity.innie.testing.BittyCityTestCase

class DepositTransitionMetricEffectsTest : BittyCityTestCase() {

  @Test
  fun `DepositFailed emits failure and state transition metric effects`() = runTest {
    val deposit = data.seedDeposit(
      state = CheckingDepositRisk,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
      sourceBalanceToken = balanceId.next()
    )

    val decision = DepositFailed(RISK_BLOCKED).decide(deposit)
    (decision is Decision.Accept) shouldBe true

    val effects = (decision as Decision.Accept).effects
    effects.contains(DepositEffect.PublishFailureReasonMetric(RISK_BLOCKED)) shouldBe true
    effects.contains(
      DepositEffect.PublishStateTransitionMetric(
        from = CheckingDepositRisk,
        to = PendingReversal,
        failureReason = RISK_BLOCKED
      )
    ) shouldBe true
  }

  @Test
  fun `ReversalFailed emits reversal failure and state transition metric effects`() = runTest {
    val deposit = data.seedDeposit(
      state = CheckingSanctions,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
      sourceBalanceToken = balanceId.next(),
      reversals = listOf(
        DepositReversal(
          token = Arbitrary.depositReversalToken.next(),
          targetWalletAddress = walletAddress.next(),
          userHasConfirmed = true
        )
      )
    )

    val decision = ReversalFailed(SANCTIONS_FAILED).decide(deposit)
    (decision is Decision.Accept) shouldBe true

    val effects = (decision as Decision.Accept).effects
    effects.contains(DepositEffect.PublishReversalFailureReasonMetric(SANCTIONS_FAILED)) shouldBe true
    effects.any {
      it is DepositEffect.PublishStateTransitionMetric &&
        it.from == CheckingSanctions &&
        it.to == PendingReversal
    } shouldBe true
  }

  @Test
  fun `DepositRiskApproved emits success amount and state transition metric effects`() = runTest {
    val deposit = data.seedDeposit(
      state = CheckingDepositRisk,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
      sourceBalanceToken = balanceId.next(),
      ledgerTransactionId = ledgerTransactionId.next()
    )

    val decision = DepositRiskApproved().decide(deposit)
    (decision is Decision.Accept) shouldBe true

    val effects = (decision as Decision.Accept).effects
    effects.any {
      it is DepositEffect.PublishDepositSuccessAmountMetric &&
        it.deposit.id == deposit.id &&
        it.deposit.state == Settled
    } shouldBe true
    effects.any {
      it is DepositEffect.PublishStateTransitionMetric &&
        it.from == CheckingDepositRisk &&
        it.to == Settled
    } shouldBe true
  }

  @Test
  fun `ReversalSanctionsDecisionFrozen passes reversal ledger transaction id to freeze effect`() = runTest {
    val reversalLedgerTransactionId = ledgerTransactionId.next()
    val deposit = data.seedDeposit(
      state = AwaitingSanctionsDecision,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
      sourceBalanceToken = balanceId.next(),
      reversals = listOf(
        DepositReversal(
          token = Arbitrary.depositReversalToken.next(),
          targetWalletAddress = walletAddress.next(),
          userHasConfirmed = true,
          ledgerTransactionId = reversalLedgerTransactionId
        )
      )
    )

    val decision = ReversalSanctionsDecisionFrozen().decide(deposit)
    (decision is Decision.Accept) shouldBe true

    val effects = (decision as Decision.Accept).effects
    effects.any {
      it is DepositEffect.FreezeReversal &&
        it.depositReversalId == deposit.currentReversal?.token &&
        it.ledgerTransactionId == reversalLedgerTransactionId
    } shouldBe true
    effects.any {
      it is DepositEffect.PublishStateTransitionMetric &&
        it.from == AwaitingSanctionsDecision &&
        it.to == Sanctioned
    } shouldBe true
  }

  @Test
  fun `ReversalSanctionsDecisionFrozen rejects when reversal ledger transaction id is missing`() = runTest {
    val deposit = data.seedDeposit(
      state = AwaitingSanctionsDecision,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
      sourceBalanceToken = balanceId.next(),
      reversals = listOf(
        DepositReversal(
          token = Arbitrary.depositReversalToken.next(),
          targetWalletAddress = walletAddress.next(),
          userHasConfirmed = true
        )
      )
    )

    val decision = ReversalSanctionsDecisionFrozen().decide(deposit)
    (decision is Decision.Reject) shouldBe true
  }
}
