package xyz.block.bittycity.innie.controllers

import app.cash.quiver.extensions.failure
import app.cash.quiver.extensions.success
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.common.client.Evaluation
import xyz.block.bittycity.common.client.RiskBlocked
import xyz.block.bittycity.innie.models.CheckingReversalRisk
import xyz.block.bittycity.innie.models.CheckingSanctions
import xyz.block.bittycity.innie.models.CollectingSanctionsInfo
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositFailureReason.RISK_BLOCKED
import xyz.block.bittycity.innie.models.DepositReversal
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.models.WaitingForReversal
import xyz.block.bittycity.innie.testing.Arbitrary
import xyz.block.bittycity.innie.testing.Arbitrary.amount
import xyz.block.bittycity.innie.testing.Arbitrary.customerId
import xyz.block.bittycity.innie.testing.Arbitrary.exchangeRate
import xyz.block.bittycity.innie.testing.Arbitrary.outputIndex
import xyz.block.bittycity.innie.testing.Arbitrary.stringToken
import xyz.block.bittycity.innie.testing.Arbitrary.walletAddress
import xyz.block.bittycity.innie.testing.BittyCityTestCase
import xyz.block.bittycity.innie.testing.shouldBeDeposit
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Operation

class ReversalSanctionsControllerTest : BittyCityTestCase() {

  @Inject lateinit var subject: ReversalSanctionsController

  @Test
  fun `should continue if sanctions approved`() = runTest {
    val deposit = data.seedDeposit(
      state = CheckingSanctions,
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

    val complete = subject.processInputs(
      deposit,
      emptyList(),
      Operation.EXECUTE
    ).getOrThrow()
    complete.shouldBeInstanceOf<ProcessingState.Complete<Deposit, RequirementId>>()
    complete.value.shouldBeDeposit(
      deposit.copy(
        state = CheckingReversalRisk
      )
    )
  }

  @Test
  fun `should fail if sanctions fail`() = runTest {
    val deposit = data.seedDeposit(
      state = CheckingSanctions,
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
    sanctionsClient.nextEvaluation = Evaluation.FAIL.success()

    subject.processInputs(deposit, emptyList(), Operation.EXECUTE)
      .shouldBeFailure<RiskBlocked>()

    depositWithToken(deposit.id) should {
      it.state shouldBe WaitingForReversal
      it.currentReversal shouldNotBeNull {
        it.failureReason shouldBe RISK_BLOCKED
      }
    }
  }

  @Test
  fun `should return failure if there is a problem calling the sanctions service`() = runTest {
    val deposit = data.seedDeposit(
      state = CheckingSanctions,
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
    sanctionsClient.nextEvaluation = RuntimeException("Something went wrong").failure()

    subject.processInputs(deposit, emptyList(), Operation.EXECUTE) shouldBeFailure {
      it.shouldBeInstanceOf<RuntimeException>()
    }
  }

  @Test
  fun `should collect sanctions info if sanctions hold`() = runTest {
    val deposit = data.seedDeposit(
      state = CheckingSanctions,
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
    sanctionsClient.nextEvaluation = Evaluation.HOLD.success()

    val complete = subject.processInputs(
      deposit,
      emptyList(),
      Operation.EXECUTE
    ).getOrThrow()
    complete.shouldBeInstanceOf<ProcessingState.Complete<Deposit, RequirementId>>()
    complete.value.shouldBeDeposit(
      deposit.copy(
        state = CollectingSanctionsInfo
      )
    )
  }
}
