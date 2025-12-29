package xyz.block.bittycity.innie.controllers

import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.innie.models.CollectingSanctionsInfo
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositFailureReason.RISK_BLOCKED
import xyz.block.bittycity.innie.models.DepositReversal
import xyz.block.bittycity.innie.models.DepositReversalHurdle.ReversalReasonHurdle
import xyz.block.bittycity.innie.models.DepositReversalHurdleResponse.ConfirmationHurdleResponse
import xyz.block.bittycity.innie.models.DepositReversalNotification
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.models.WaitingForSanctionsHeldDecision
import xyz.block.bittycity.innie.testing.Arbitrary
import xyz.block.bittycity.innie.testing.Arbitrary.amount
import xyz.block.bittycity.innie.testing.Arbitrary.customerId
import xyz.block.bittycity.innie.testing.Arbitrary.exchangeRate
import xyz.block.bittycity.innie.testing.Arbitrary.outputIndex
import xyz.block.bittycity.innie.testing.Arbitrary.stringToken
import xyz.block.bittycity.innie.testing.Arbitrary.walletAddress
import xyz.block.bittycity.innie.testing.BittyCityTestCase
import xyz.block.bittycity.innie.validation.ValidationService.MAX_REVERSAL_REASON_LENGTH
import xyz.block.domainapi.DomainApi.Endpoint.SECURE_EXECUTE
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.ResultCode.CLEARED
import xyz.block.domainapi.util.Operation

class ReversalSanctionsInfoCollectionControllerTest : BittyCityTestCase() {

  @Inject lateinit var subject: ReversalSanctionsInfoCollectionController

  @Test
  fun `returns hurdles when no reason for reversal present`() = runTest {
    val deposit = data.seedDeposit(
      state = CollectingSanctionsInfo,
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

    val result = subject.processInputs(
      deposit,
      emptyList(),
      Operation.EXECUTE
    ).getOrThrow()
    result shouldBe ProcessingState.UserInteractions(
      hurdles = listOf(ReversalReasonHurdle(MAX_REVERSAL_REASON_LENGTH)),
      nextEndpoint = SECURE_EXECUTE
    )
  }

  @Test
  fun `returns hurdles when reason for reversal is empty`() = runTest {
    val deposit = data.seedDeposit(
      state = CollectingSanctionsInfo,
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
          targetWalletAddress = walletAddress.next(),
          reasonForReversal = ""
        )
      )
    ) {
      it.copy(failureReason = RISK_BLOCKED)
    }

    val result = subject.processInputs(
      deposit,
      emptyList(),
      Operation.EXECUTE
    ).getOrThrow()
    val complete =
      result.shouldBeInstanceOf<ProcessingState.UserInteractions<Deposit, RequirementId>>()
    complete.hurdles.size shouldBe 1
    complete.hurdles[0] shouldBe DepositReversalNotification.DepositReversalSanctionsHeld

    depositWithToken(deposit.id)
      .state shouldBe WaitingForSanctionsHeldDecision
  }

  @Test
  fun `transitions if reason for reversal is present`() = runTest {
    val deposit = data.seedDeposit(
      state = CollectingSanctionsInfo,
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
          targetWalletAddress = walletAddress.next(),
          reasonForReversal = "I want my free iPhone"
        )
      )
    ) {
      it.copy(failureReason = RISK_BLOCKED)
    }

    val result = subject.processInputs(
      deposit,
      emptyList(),
      Operation.EXECUTE
    ).getOrThrow()
    val complete =
      result.shouldBeInstanceOf<ProcessingState.UserInteractions<Deposit, RequirementId>>()
    complete.hurdles.size shouldBe 1
    complete.hurdles[0] shouldBe DepositReversalNotification.DepositReversalSanctionsHeld

    depositWithToken(deposit.id)
      .state shouldBe WaitingForSanctionsHeldDecision
  }

  @Test
  fun `reversal is not failed if something fails`() = runTest {
    val deposit = data.seedDeposit(
      state = CollectingSanctionsInfo,
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

    subject.processInputs(
      deposit,
      listOf(ConfirmationHurdleResponse(CLEARED)), // Force an error - this is an invalid input
      Operation.EXECUTE
    ).shouldBeFailure<DomainApiError.InvalidRequirementResult>()

    depositWithToken(deposit.id)
      .state shouldBe CollectingSanctionsInfo
  }
}
