package xyz.block.bittycity.innie.controllers

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.innie.api.DepositDomainController
import xyz.block.bittycity.innie.models.CollectingReversalInfo
import xyz.block.bittycity.innie.models.DepositFailureReason.RISK_BLOCKED
import xyz.block.bittycity.innie.models.DepositReversal
import xyz.block.bittycity.innie.models.DepositReversalHurdle
import xyz.block.bittycity.innie.testing.Arbitrary
import xyz.block.bittycity.innie.testing.Arbitrary.amount
import xyz.block.bittycity.innie.testing.Arbitrary.balanceId
import xyz.block.bittycity.innie.testing.Arbitrary.customerId
import xyz.block.bittycity.innie.testing.Arbitrary.exchangeRate
import xyz.block.bittycity.innie.testing.Arbitrary.outputIndex
import xyz.block.bittycity.innie.testing.Arbitrary.stringToken
import xyz.block.bittycity.innie.testing.Arbitrary.walletAddress
import xyz.block.bittycity.innie.testing.BittyCityTestCase
import xyz.block.domainapi.kfsm.v2.util.Operation

class DepositReversalControllerTest : BittyCityTestCase() {

  @Inject lateinit var subject: DepositDomainController

  @Test
  fun `Starting a reversal returns hurdles for target wallet address and confirmation`() = runTest {
    val deposit = data.seedDeposit(
      state = CollectingReversalInfo,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
      sourceBalanceToken = balanceId.next(),
      reversals = listOf(
        DepositReversal(Arbitrary.depositReversalToken.next())
      )
    ) {
      it.copy(failureReason = RISK_BLOCKED)
    }

    val response = subject.execute(deposit, emptyList(), Operation.EXECUTE).shouldBeSuccess()
    response.interactions shouldHaveSize 1
    response.interactions[0] shouldBe DepositReversalHurdle.TargetWalletAddressHurdle

    depositWithToken(deposit.id) should {
      it.state shouldBe CollectingReversalInfo
      it.failureReason shouldBe RISK_BLOCKED
    }
  }
}
