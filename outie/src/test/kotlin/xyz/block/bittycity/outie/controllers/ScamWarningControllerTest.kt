package xyz.block.bittycity.outie.controllers

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.outie.models.CheckingTravelRule
import xyz.block.bittycity.outie.models.CollectingScamWarningDecision
import xyz.block.bittycity.outie.models.Failed
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.WithdrawalHurdleResponse
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import xyz.block.bittycity.outie.testing.shouldBeWithdrawal
import xyz.block.domainapi.DomainApiError.UnsupportedHurdleResultCode
import xyz.block.domainapi.ResultCode
import xyz.block.domainapi.ResultCode.FINISHED_OK

class ScamWarningControllerTest : BittyCityTestCase() {

  @Inject lateinit var subject: ScamWarningController

  @Test
  fun `should cancel`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CollectingScamWarningDecision,
      walletAddress = Arbitrary.walletAddress.next(),
      amount = Arbitrary.bitcoins.next(),
    )

    val updated = subject.onCancel(withdrawal).getOrThrow()

    updated.state shouldBe Failed
    updated.failureReason shouldBe FailureReason.CUSTOMER_CANCELLED

    withdrawalWithToken(withdrawal.id).shouldBeWithdrawal(updated)
  }

  @Test
  fun `should find missing requirements`() = runTest {
    subject.findMissingRequirements(
      Arbitrary.withdrawal.next().copy(userHasAcceptedRisk = null)
    ).getOrThrow().shouldContainExactly(RequirementId.SCAM_WARNING)

    subject.findMissingRequirements(
      Arbitrary.withdrawal.next().copy(userHasAcceptedRisk = true)
    ).getOrThrow().shouldBeEmpty()

    subject.findMissingRequirements(
      Arbitrary.withdrawal.next().copy(userHasAcceptedRisk = false)
    ).getOrThrow().shouldBeEmpty()
  }

  @Test
  fun `should fail when response code not handled`() = runTest {
    val withdrawal = data.seedWithdrawal()

    subject
      .updateValue(withdrawal, WithdrawalHurdleResponse.ScamWarningHurdleResponse(FINISHED_OK))
      .shouldBeFailure(UnsupportedHurdleResultCode(withdrawal.customerId.id, FINISHED_OK))
    withdrawalWithToken(withdrawal.id).version shouldBe withdrawal.version
  }

  @Test
  fun `should fail when response type is not handled`() = runTest {
    val withdrawal = data.seedWithdrawal()

    subject
      .updateValue(
        withdrawal,
        WithdrawalHurdleResponse.SpeedHurdleResponse(ResultCode.CLEARED, WithdrawalSpeed.PRIORITY)
      )
      .shouldBeFailure<IllegalArgumentException>()
    withdrawalWithToken(withdrawal.id).version shouldBe withdrawal.version
  }

  @Test
  fun `should transition when risk accepted`() = runTest {
    val withdrawal = data.seedWithdrawal(state = CollectingScamWarningDecision) {
      it.copy(userHasAcceptedRisk = true)
    }

    val updated = subject.transition(withdrawal).getOrThrow()

    updated.state shouldBe CheckingTravelRule
  }

  @Test
  fun `should transition when risk rejected`() = runTest {
    val withdrawal = data.seedWithdrawal(state = CollectingScamWarningDecision) {
      it.copy(userHasAcceptedRisk = false)
    }

    val updated = subject.transition(withdrawal).getOrThrow()

    updated.state shouldBe Failed
    updated.failureReason shouldBe FailureReason.CUSTOMER_DECLINED_DUE_TO_SCAM_WARNING
  }
}
