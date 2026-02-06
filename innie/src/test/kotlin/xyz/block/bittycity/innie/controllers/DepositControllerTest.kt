package xyz.block.bittycity.innie.controllers

import app.cash.quiver.extensions.success
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.common.client.Eligibility
import xyz.block.bittycity.common.client.RiskEvaluation
import xyz.block.bittycity.innie.api.DepositDomainController
import xyz.block.bittycity.innie.models.CollectingReversalInfo
import xyz.block.bittycity.innie.models.DepositFailureReason.INELIGIBLE
import xyz.block.bittycity.innie.models.DepositFailureReason.RISK_BLOCKED
import xyz.block.bittycity.innie.models.DepositFailureReason.UNEXPECTED_RISK_RESULT
import xyz.block.bittycity.innie.models.DepositResumeResult
import xyz.block.bittycity.innie.models.DepositSettled
import xyz.block.bittycity.innie.models.WaitingForDepositConfirmedOnChainStatus
import xyz.block.bittycity.innie.testing.Arbitrary.amount
import xyz.block.bittycity.innie.testing.Arbitrary.balanceId
import xyz.block.bittycity.innie.testing.Arbitrary.customerId
import xyz.block.bittycity.innie.testing.Arbitrary.exchangeRate
import xyz.block.bittycity.innie.testing.Arbitrary.ledgerTransactionId
import xyz.block.bittycity.innie.testing.Arbitrary.outputIndex
import xyz.block.bittycity.innie.testing.Arbitrary.stringToken
import xyz.block.bittycity.innie.testing.Arbitrary.walletAddress
import xyz.block.bittycity.innie.testing.BittyCityTestCase
import xyz.block.domainapi.kfsm.v2.util.Operation

class DepositControllerTest : BittyCityTestCase() {

  @Inject lateinit var subject: DepositDomainController

  @Test
  fun `Fail if risk blocked`() = runTest {
    val deposit = data.seedDeposit(
      state = WaitingForDepositConfirmedOnChainStatus,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
      sourceBalanceToken = balanceId.next()
    )

    val resumeResult = DepositResumeResult.ConfirmedOnChain(
      depositToken = deposit.id,
      paymentToken = deposit.paymentToken,
      targetWalletAddress = deposit.targetWalletAddress,
      amount = deposit.amount,
      blockchainTransactionId = deposit.blockchainTransactionId,
      blockchainTransactionOutputIndex = deposit.blockchainTransactionOutputIndex,
    )

    eligibilityClient.nextEligibilityResult = Eligibility.Eligible(emptyList()).success()
    riskClient.nextRiskResult = RiskEvaluation.Blocked().success()

    subject.execute(deposit, listOf(resumeResult), Operation.RESUME).getOrThrow()
    app.processAllEffects()

    depositWithToken(deposit.id) should {
      it.state shouldBe CollectingReversalInfo
      it.failureReason shouldBe RISK_BLOCKED
    }
  }

  @Test
  fun `Fail if unexpected risk result`() = runTest {
    val deposit = data.seedDeposit(
      state = WaitingForDepositConfirmedOnChainStatus,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
      sourceBalanceToken = balanceId.next()
    )

    val resumeResult = DepositResumeResult.ConfirmedOnChain(
      depositToken = deposit.id,
      paymentToken = deposit.paymentToken,
      targetWalletAddress = deposit.targetWalletAddress,
      amount = deposit.amount,
      blockchainTransactionId = deposit.blockchainTransactionId,
      blockchainTransactionOutputIndex = deposit.blockchainTransactionOutputIndex,
    )

    eligibilityClient.nextEligibilityResult = Eligibility.Eligible(emptyList()).success()
    riskClient.nextRiskResult = RiskEvaluation.ActiveScamWarning().success()

    subject.execute(deposit, listOf(resumeResult), Operation.RESUME).getOrThrow()
    app.processAllEffects()

    depositWithToken(deposit.id) should {
      it.state shouldBe CollectingReversalInfo
      it.failureReason shouldBe UNEXPECTED_RISK_RESULT
    }
  }

  @Test
  fun `Continue after risk check is ok`() = runTest {
    val deposit = data.seedDeposit(
      state = WaitingForDepositConfirmedOnChainStatus,
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

    val resumeResult = DepositResumeResult.ConfirmedOnChain(
      depositToken = deposit.id,
      paymentToken = deposit.paymentToken,
      targetWalletAddress = deposit.targetWalletAddress,
      amount = deposit.amount,
      blockchainTransactionId = deposit.blockchainTransactionId,
      blockchainTransactionOutputIndex = deposit.blockchainTransactionOutputIndex,
    )

    eligibilityClient.nextEligibilityResult = Eligibility.Eligible(emptyList()).success()
    riskClient.nextRiskResult = RiskEvaluation.Checked.success()

    subject.execute(deposit, listOf(resumeResult), Operation.RESUME).getOrThrow()
    app.processAllEffects()

    depositWithToken(deposit.id) should {
      it.state shouldBe DepositSettled
    }
  }

  @Test
  fun `Fail when not eligible`() = runTest {
    val deposit = data.seedDeposit(
      state = WaitingForDepositConfirmedOnChainStatus,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
      sourceBalanceToken = balanceId.next()
    )

    val resumeResult = DepositResumeResult.ConfirmedOnChain(
      depositToken = deposit.id,
      paymentToken = deposit.paymentToken,
      targetWalletAddress = deposit.targetWalletAddress,
      amount = deposit.amount,
      blockchainTransactionId = deposit.blockchainTransactionId,
      blockchainTransactionOutputIndex = deposit.blockchainTransactionOutputIndex,
    )

    eligibilityClient.nextEligibilityResult = Eligibility.Ineligible(emptyList()).success()

    subject.execute(deposit, listOf(resumeResult), Operation.RESUME).getOrThrow()
    app.processAllEffects()

    depositWithToken(deposit.id) should {
      it.state shouldBe CollectingReversalInfo
      it.failureReason shouldBe INELIGIBLE
    }
  }
}
