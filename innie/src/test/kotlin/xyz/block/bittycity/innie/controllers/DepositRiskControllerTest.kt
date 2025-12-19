package xyz.block.bittycity.innie.controllers

import app.cash.quiver.extensions.success
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.common.client.RiskEvaluation
import xyz.block.bittycity.innie.api.DepositDomainController
import xyz.block.bittycity.innie.models.CheckingDepositRisk
import xyz.block.bittycity.innie.models.DepositFailureReason.RISK_BLOCKED
import xyz.block.bittycity.innie.models.Settled
import xyz.block.bittycity.innie.models.WaitingForReversal
import xyz.block.bittycity.innie.testing.Arbitrary.amount
import xyz.block.bittycity.innie.testing.Arbitrary.customerId
import xyz.block.bittycity.innie.testing.Arbitrary.exchangeRate
import xyz.block.bittycity.innie.testing.Arbitrary.outputIndex
import xyz.block.bittycity.innie.testing.Arbitrary.stringToken
import xyz.block.bittycity.innie.testing.Arbitrary.walletAddress
import xyz.block.bittycity.innie.testing.BittyCityTestCase
import xyz.block.domainapi.util.Operation

class DepositRiskControllerTest : BittyCityTestCase() {

  @Inject lateinit var subject: DepositDomainController

  @Test
  fun `Fail with RISK_BLOCKED`() = runTest {
    val deposit = data.seedDeposit(
      state = CheckingDepositRisk,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
    )

    riskClient.nextRiskResult = RiskEvaluation.Blocked().success()

    subject.execute(deposit, emptyList(), Operation.EXECUTE).shouldBeSuccess()

    depositWithToken(deposit.id) should {
      it.state shouldBe WaitingForReversal
      it.failureReason shouldBe RISK_BLOCKED
    }
  }

  @Test
  fun `Continue after risk check is ok`() = runTest {
    val deposit = data.seedDeposit(
      state = CheckingDepositRisk,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
    )

    riskClient.nextRiskResult = RiskEvaluation.Checked.success()

    subject.execute(deposit, emptyList(), Operation.EXECUTE).shouldBeSuccess()

    depositWithToken(deposit.id) should {
      it.state shouldBe Settled
    }
  }
}