package xyz.block.bittycity.innie.controllers

import app.cash.quiver.extensions.success
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.common.client.RiskBlocked
import xyz.block.bittycity.common.client.RiskEvaluation
import xyz.block.bittycity.innie.api.DepositDomainController
import xyz.block.bittycity.innie.models.CheckingReversalRisk
import xyz.block.bittycity.innie.models.DepositFailureReason.RISK_BLOCKED
import xyz.block.bittycity.innie.models.DepositReversal
import xyz.block.bittycity.innie.models.DepositReversalFailureReason
import xyz.block.bittycity.innie.models.WaitingForReversal
import xyz.block.bittycity.innie.models.WaitingForReversalPendingConfirmationStatus
import xyz.block.bittycity.innie.testing.Arbitrary
import xyz.block.bittycity.innie.testing.Arbitrary.amount
import xyz.block.bittycity.innie.testing.Arbitrary.customerId
import xyz.block.bittycity.innie.testing.Arbitrary.exchangeRate
import xyz.block.bittycity.innie.testing.Arbitrary.outputIndex
import xyz.block.bittycity.innie.testing.Arbitrary.stringToken
import xyz.block.bittycity.innie.testing.Arbitrary.walletAddress
import xyz.block.bittycity.innie.testing.BittyCityTestCase
import xyz.block.domainapi.util.Operation

class ReversalRiskControllerTest : BittyCityTestCase() {

  @Inject lateinit var subject: DepositDomainController

  @Test
  fun `Fail with RISK_BLOCKED`() = runTest {
    val deposit = data.seedDeposit(
      state = CheckingReversalRisk,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
      reversals = listOf(
        DepositReversal(
          token = Arbitrary.depositReversalToken.next(),
          targetWalletAddress = walletAddress.next()
        )
      )
    ) {
      it.copy(failureReason = RISK_BLOCKED)
    }

    reversalRiskClient.nextRiskResult = RiskEvaluation.Blocked().success()

    subject.execute(deposit, emptyList(), Operation.EXECUTE).shouldBeFailure<RiskBlocked>()

    depositWithToken(deposit.id) should {
      it.state shouldBe WaitingForReversal
      it.currentReversal shouldNotBeNull {
        it.currentReversal?.failureReason shouldBe DepositReversalFailureReason.RISK_BLOCKED
      }
    }
  }

  @Test
  fun `Unexpected scam warning fails reversal`() = runTest {
    val deposit = data.seedDeposit(
      state = CheckingReversalRisk,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
      reversals = listOf(
        DepositReversal(
          token = Arbitrary.depositReversalToken.next(),
          targetWalletAddress = walletAddress.next()
        )
      )
    ) {
      it.copy(failureReason = RISK_BLOCKED)
    }

    reversalRiskClient.nextRiskResult = RiskEvaluation.ActiveScamWarning().success()

    subject.execute(deposit, emptyList(), Operation.EXECUTE).shouldBeFailure<IllegalStateException>()

    depositWithToken(deposit.id)
      .state shouldBe WaitingForReversal
  }

  @Test
  fun `Continue after risk check is ok`() = runTest {
    val deposit = data.seedDeposit(
      state = CheckingReversalRisk,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
      reversals = listOf(
        DepositReversal(
          token = Arbitrary.depositReversalToken.next(),
          targetWalletAddress = walletAddress.next()
        )
      )
    ) {
      it.copy(failureReason = RISK_BLOCKED)
    }

    subject.execute(deposit, emptyList(), Operation.EXECUTE).getOrThrow()

    depositWithToken(deposit.id)
      .state shouldBe WaitingForReversalPendingConfirmationStatus
  }
}
