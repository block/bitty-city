package xyz.block.bittycity.innie.controllers

import app.cash.quiver.extensions.failure
import app.cash.quiver.extensions.success
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.common.client.Evaluation
import xyz.block.bittycity.innie.api.DepositDomainController
import xyz.block.bittycity.innie.models.CheckingReversalRisk
import xyz.block.bittycity.innie.models.CheckingReversalSanctions
import xyz.block.bittycity.innie.models.CollectingReversalInfo
import xyz.block.bittycity.innie.models.CollectingReversalSanctionsInfo
import xyz.block.bittycity.innie.models.DepositFailureReason.RISK_BLOCKED
import xyz.block.bittycity.innie.models.DepositReversal
import xyz.block.bittycity.innie.models.DepositReversalFailureReason
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

class ReversalSanctionsControllerTest : BittyCityTestCase() {

  @Inject lateinit var subject: DepositDomainController

  @Test
  fun `should continue if sanctions approved`() = runTest {
    val reversalToken = Arbitrary.depositReversalToken.next()
    val reversalTargetWallet = walletAddress.next()

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
        DepositReversal(
          token = reversalToken,
          targetWalletAddress = reversalTargetWallet,
          userHasConfirmed = true
        )
      )
    ) {
      it.copy(failureReason = RISK_BLOCKED)
    }

    sanctionsClient.nextEvaluation = Evaluation.APPROVE.success()

    subject.execute(deposit, emptyList(), Operation.EXECUTE).getOrThrow()
    app.effectProcessor.processAll()

    depositWithToken(deposit.id).state shouldBe CheckingReversalRisk
  }

  @Test
  fun `should fail if sanctions fail`() = runTest {
    val reversalToken = Arbitrary.depositReversalToken.next()
    val reversalTargetWallet = walletAddress.next()

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
        DepositReversal(
          token = reversalToken,
          targetWalletAddress = reversalTargetWallet,
          userHasConfirmed = true
        )
      )
    ) {
      it.copy(failureReason = RISK_BLOCKED)
    }

    sanctionsClient.nextEvaluation = Evaluation.FAIL.success()

    subject.execute(deposit, emptyList(), Operation.EXECUTE).getOrThrow()
    app.processAllEffects()

    depositWithToken(deposit.id) should {
      it.state shouldBe CollectingReversalInfo
      it.currentReversal.shouldNotBeNull()
      it.currentReversal?.failureReason shouldBe DepositReversalFailureReason.SANCTIONS_FAILED
    }
  }

  @Test
  fun `should return failure if there is a problem calling the sanctions service`() = runTest {
    val reversalToken = Arbitrary.depositReversalToken.next()
    val reversalTargetWallet = walletAddress.next()

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
        DepositReversal(
          token = reversalToken,
          targetWalletAddress = reversalTargetWallet,
          userHasConfirmed = true
        )
      )
    ) {
      it.copy(failureReason = RISK_BLOCKED)
    }

    sanctionsClient.nextEvaluation = RuntimeException("Something went wrong").failure()

    subject.execute(deposit, emptyList(), Operation.EXECUTE).getOrThrow()
    app.processAllEffects()

    depositWithToken(deposit.id).state shouldBe CheckingReversalSanctions
  }

  @Test
  fun `should collect sanctions info if sanctions hold`() = runTest {
    val reversalToken = Arbitrary.depositReversalToken.next()
    val reversalTargetWallet = walletAddress.next()

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
        DepositReversal(
          token = reversalToken,
          targetWalletAddress = reversalTargetWallet,
          userHasConfirmed = true
        )
      )
    ) {
      it.copy(failureReason = RISK_BLOCKED)
    }

    sanctionsClient.nextEvaluation = Evaluation.HOLD.success()

    subject.execute(deposit, emptyList(), Operation.EXECUTE).getOrThrow()
    app.processAllEffects()

    depositWithToken(deposit.id).state shouldBe CollectingReversalSanctionsInfo
  }
}
