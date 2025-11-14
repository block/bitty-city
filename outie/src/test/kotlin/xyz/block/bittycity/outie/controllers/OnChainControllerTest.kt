package xyz.block.bittycity.outie.controllers

import app.cash.quiver.extensions.success
import xyz.block.bittycity.outie.client.LimitResponse
import xyz.block.bittycity.outie.client.LimitViolation
import xyz.block.bittycity.outie.models.ConfirmedComplete
import xyz.block.bittycity.outie.models.ConfirmedOnChain
import xyz.block.bittycity.outie.models.Failed
import xyz.block.bittycity.outie.models.FailedOnChain
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.HoldingSubmission
import xyz.block.bittycity.outie.models.LedgerEntryToken
import xyz.block.bittycity.outie.models.ObservedInMempool
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.SanctionsHeldDecision
import xyz.block.bittycity.outie.models.SanctionsReviewDecision
import xyz.block.bittycity.outie.models.SubmittingOnChain
import xyz.block.bittycity.outie.models.WaitingForConfirmedOnChainStatus
import xyz.block.bittycity.outie.models.WaitingForPendingConfirmationStatus
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.Arbitrary.amount
import xyz.block.bittycity.outie.testing.Arbitrary.walletAddress
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import xyz.block.bittycity.outie.testing.shouldBeWithdrawal
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Operation

class OnChainControllerTest : BittyCityTestCase() {

  @Inject lateinit var subject: OnChainController

  @Test
  fun `on complete will execute submission`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = SubmittingOnChain,
      withdrawalSpeed = WithdrawalSpeed.RUSH,
      walletAddress = walletAddress.next(),
      amount = amount.next(),
    )

    subject.processInputs(withdrawal, emptyList(), Operation.EXECUTE)
      .getOrThrow()
      .shouldBeInstanceOf<ProcessingState.UserInteractions<Withdrawal, RequirementId>>()

    withdrawalWithToken(withdrawal.id)
      .shouldBeWithdrawal(withdrawal.copy(state = WaitingForPendingConfirmationStatus))

    // Process outbox
    processWithdrawalEvents()

    onChainService.getSubmittedWithdrawal(withdrawal.id).shouldNotBeNull()
      .destinationAddress shouldBe withdrawal.targetWalletAddress

    withdrawalWithToken(withdrawal.id)
      .shouldNotBeNull()
      .state shouldBe WaitingForPendingConfirmationStatus
  }

  @Test
  fun `should transition when withdrawal is observed in mempool`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = WaitingForPendingConfirmationStatus,
      withdrawalSpeed = WithdrawalSpeed.RUSH,
      walletAddress = walletAddress.next(),
      amount = amount.next(),
    )
    val resumeResult = ObservedInMempool(Arbitrary.stringToken.next(), Arbitrary.outputIndex.next())

    val updated = subject.processInputs(
      withdrawal,
      listOf(resumeResult),
      Operation.EXECUTE
    )
      .getOrThrow()
      .shouldBeInstanceOf<ProcessingState.Waiting<Withdrawal, RequirementId>>()
    updated.value shouldBeWithdrawal withdrawal.copy(
      state = WaitingForConfirmedOnChainStatus,
      blockchainTransactionId = resumeResult.blockchainTransactionId,
      blockchainTransactionOutputIndex = resumeResult.blockchainTransactionOutputIndex
    )
  }

  @Test
  fun `should transition when withdrawal is confirmed in the blockchain`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = WaitingForConfirmedOnChainStatus,
      withdrawalSpeed = WithdrawalSpeed.RUSH,
      walletAddress = walletAddress.next(),
      amount = amount.next(),
      ledgerEntryToken = LedgerEntryToken(Arbitrary.stringToken.next())
    )
    val resumeResult = ConfirmedOnChain(Arbitrary.stringToken.next(), Arbitrary.outputIndex.next())

    val updated = subject.processInputs(
      withdrawal,
      listOf(resumeResult),
      Operation.EXECUTE
    )
      .getOrThrow()
      .shouldBeInstanceOf<ProcessingState.Complete<Withdrawal, RequirementId>>()
    updated.value shouldBeWithdrawal withdrawal.copy(
      state = ConfirmedComplete,
      blockchainTransactionId = resumeResult.blockchainTransactionId,
      blockchainTransactionOutputIndex = resumeResult.blockchainTransactionOutputIndex
    )
  }

  @Test
  fun `should transition if confirmed before observing in mempool`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = WaitingForPendingConfirmationStatus,
      withdrawalSpeed = WithdrawalSpeed.RUSH,
      walletAddress = walletAddress.next(),
      amount = amount.next(),
      ledgerEntryToken = LedgerEntryToken(Arbitrary.stringToken.next())
    )
    val resumeResult = ConfirmedOnChain(Arbitrary.stringToken.next(), Arbitrary.outputIndex.next())

    val updated = subject.processInputs(
      withdrawal,
      listOf(resumeResult),
      Operation.EXECUTE
    )
      .getOrThrow()
      .shouldBeInstanceOf<ProcessingState.Complete<Withdrawal, RequirementId>>()
    updated.value shouldBeWithdrawal withdrawal.copy(
      state = ConfirmedComplete,
      blockchainTransactionId = resumeResult.blockchainTransactionId,
      blockchainTransactionOutputIndex = resumeResult.blockchainTransactionOutputIndex
    )
  }

  @Test
  fun `should not fail if observed in mempool when it has already been observed`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = WaitingForConfirmedOnChainStatus,
      withdrawalSpeed = WithdrawalSpeed.RUSH,
      walletAddress = walletAddress.next(),
      amount = amount.next(),
    )
    val resumeResult = ObservedInMempool(Arbitrary.stringToken.next(), Arbitrary.outputIndex.next())
    val updated = subject.processInputs(
      withdrawal,
      listOf(resumeResult),
      Operation.EXECUTE
    )
      .getOrThrow()
      .shouldBeInstanceOf<ProcessingState.Waiting<Withdrawal, RequirementId>>()
    updated.value shouldBeWithdrawal withdrawal
  }

  @Test
  fun `should fail with LIMITED reason when withdrawal would exceed limits`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = HoldingSubmission,
      withdrawalSpeed = WithdrawalSpeed.RUSH,
      amount = amount.next(),
    )

    // Configure the limit client to return a limited response
    limitClient.nextLimitResponse =
      LimitResponse.Limited(listOf(LimitViolation.DAILY_USD_LIMIT)).success()

    subject.processInputs(withdrawal, emptyList(), Operation.EXECUTE)
      .shouldBeFailure<LimitWouldBeExceeded>()

    // Verify the withdrawal was stored with the correct state
    withdrawalWithToken(withdrawal.id) should {
      it.state shouldBe Failed
      it.failureReason shouldBe FailureReason.LIMITED
    }
  }

  @Test
  fun `withdrawal waiting to be observed in the mempool should fail if withdrawal fails`() =
    runTest {
      val withdrawal = data.seedWithdrawal(
        state = WaitingForPendingConfirmationStatus,
        withdrawalSpeed = WithdrawalSpeed.RUSH,
        amount = amount.next(),
      )

      val resumeResult = FailedOnChain(null, null)

      subject.processInputs(withdrawal, listOf(resumeResult), Operation.EXECUTE)
        .getOrThrow()

      withdrawalWithToken(withdrawal.id) should {
        it.state shouldBe Failed
        it.failureReason shouldBe FailureReason.FAILED_ON_CHAIN
      }
    }

  @Test
  fun `withdrawal waiting to be confirmed should fail if withdrawal fails`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = WaitingForConfirmedOnChainStatus,
      withdrawalSpeed = WithdrawalSpeed.RUSH,
      amount = amount.next(),
    )

    val resumeResult = FailedOnChain(null, null)

    subject.processInputs(withdrawal, listOf(resumeResult), Operation.EXECUTE)
      .getOrThrow()

    withdrawalWithToken(withdrawal.id) should {
      it.state shouldBe Failed
      it.failureReason shouldBe FailureReason.FAILED_ON_CHAIN
    }
  }

  @Test
  fun `withdrawal is not failed if something fails and it has been submitted on-chain`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = WaitingForPendingConfirmationStatus,
    )

    val resumeResult = SanctionsHeldDecision(SanctionsReviewDecision.FREEZE)

    // Force an error - this is an invalid input
    onChainWithdrawalDomainApi.resume(withdrawal.id, resumeResult)
      .shouldBeFailure<IllegalArgumentException>()

    withdrawalWithToken(withdrawal.id)
      .state shouldBe WaitingForPendingConfirmationStatus
  }

  @Test
  fun `withdrawal is failed if something fails and it has not been submitted on-chain`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = HoldingSubmission,
    )

    // Simulate an error
    limitClient.failNextEvaluation()

    onChainWithdrawalDomainApi.execute(withdrawal.id, emptyList())
      .shouldBeFailure<Exception>()

    withdrawalWithToken(withdrawal.id) should {
      it.state shouldBe Failed
      it.failureReason shouldBe FailureReason.UNKNOWN
    }
  }

  @Test
  fun `on complete should not fail if withdrawal is already complete`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = ConfirmedComplete,
      withdrawalSpeed = WithdrawalSpeed.RUSH,
      walletAddress = walletAddress.next(),
      amount = amount.next(),
    )

    subject.processInputs(withdrawal, emptyList(), Operation.EXECUTE)
      .getOrThrow()
      .shouldBeInstanceOf<ProcessingState.Complete<Withdrawal, RequirementId>>()

    withdrawalWithToken(withdrawal.id)
      .shouldBeWithdrawal(withdrawal.copy(state = ConfirmedComplete))
  }
}
