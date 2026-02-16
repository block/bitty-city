package xyz.block.bittycity.innie.fsm

import app.cash.kfsm.v2.Decision
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import org.junit.jupiter.api.Test
import xyz.block.bittycity.innie.models.CheckingDepositRisk
import xyz.block.bittycity.innie.models.Settled
import xyz.block.bittycity.innie.testing.Arbitrary
import xyz.block.bittycity.innie.testing.Arbitrary.amount
import xyz.block.bittycity.innie.testing.Arbitrary.balanceId
import xyz.block.bittycity.innie.testing.Arbitrary.customerId
import xyz.block.bittycity.innie.testing.Arbitrary.exchangeRate
import xyz.block.bittycity.innie.testing.Arbitrary.outputIndex
import xyz.block.bittycity.innie.testing.Arbitrary.stringToken
import xyz.block.bittycity.innie.testing.Arbitrary.walletAddress
import xyz.block.bittycity.innie.testing.BittyCityTestCase

class DepositSuccessMetricEffectsTest : BittyCityTestCase() {

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
      ledgerTransactionId = Arbitrary.ledgerTransactionId.next()
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
}
