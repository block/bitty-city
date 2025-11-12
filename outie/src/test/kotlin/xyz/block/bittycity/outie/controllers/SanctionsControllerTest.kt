package xyz.block.bittycity.outie.controllers

import app.cash.quiver.extensions.failure
import app.cash.quiver.extensions.success
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.outie.client.Evaluation
import xyz.block.bittycity.outie.models.CheckingRisk
import xyz.block.bittycity.outie.models.CheckingSanctions
import xyz.block.bittycity.outie.models.CollectingSanctionsInfo
import xyz.block.bittycity.outie.models.Failed
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.LedgerEntryToken
import xyz.block.bittycity.common.models.LedgerTransactionId
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import xyz.block.bittycity.outie.testing.shouldBeWithdrawal
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Operation

class SanctionsControllerTest : BittyCityTestCase() {

  @Inject lateinit var subject: SanctionsController

  @Test
  fun `should continue if sanctions approved`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CheckingSanctions,
      walletAddress = Arbitrary.walletAddress.next()
    )

    val complete = subject.processInputs(
      withdrawal,
      emptyList(),
      Operation.EXECUTE
    ).getOrThrow()
    complete.shouldBeInstanceOf<ProcessingState.Complete<Withdrawal, RequirementId>>()
    complete.value.shouldBeWithdrawal(
      withdrawal.copy(
        state = CheckingRisk
      )
    )
  }

  @Test
  fun `should fail if sanctions fail`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CheckingSanctions,
      walletAddress = Arbitrary.walletAddress.next()
    )
    sanctionsClient.nextEvaluation = Evaluation.FAIL.success()

    this@SanctionsControllerTest.subject.processInputs(withdrawal, emptyList(), Operation.EXECUTE)
      .shouldBeFailure<RiskBlocked>()

    withdrawalStore.getWithdrawalByToken(withdrawal.id).getOrThrow() should {
      it.state shouldBe Failed
      it.failureReason shouldBe FailureReason.SANCTIONS_FAILED
    }
  }

  @Test
  fun `should return failure if there is a problem calling the sanctions service`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CheckingSanctions,
      walletAddress = Arbitrary.walletAddress.next()
    )
    sanctionsClient.nextEvaluation = RuntimeException("Something went wrong").failure()

    subject.processInputs(withdrawal, emptyList(), Operation.EXECUTE) shouldBeFailure {
      it.shouldBeInstanceOf<RuntimeException>()
    }
  }

  @Test
  fun `should refund fee for withdrawal if hold`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CheckingSanctions,
      walletAddress = Arbitrary.walletAddress.next(),
      ledgerEntryToken = LedgerEntryToken("12345")
    )
    sanctionsClient.nextEvaluation = Evaluation.HOLD.success()

    val complete = subject.processInputs(
      withdrawal,
      emptyList(),
      Operation.EXECUTE
    ).getOrThrow()
    complete.shouldBeInstanceOf<ProcessingState.Complete<Withdrawal, RequirementId>>()
    complete.value.shouldBeWithdrawal(
      withdrawal.copy(
        state = CollectingSanctionsInfo,
        feeRefunded = true,
        ledgerTransactionId = LedgerTransactionId("testTransactionId")
      )
    )
  }
}
