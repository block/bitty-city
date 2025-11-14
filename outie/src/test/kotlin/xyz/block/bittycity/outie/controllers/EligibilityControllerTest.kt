package xyz.block.bittycity.outie.controllers

import app.cash.quiver.extensions.success
import xyz.block.bittycity.outie.client.Eligibility
import xyz.block.bittycity.outie.models.CheckingEligibility
import xyz.block.bittycity.outie.models.Failed
import xyz.block.bittycity.outie.models.FailureReason.CUSTOMER_IS_INELIGIBLE
import xyz.block.bittycity.outie.models.WaitingForPendingConfirmationStatus
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.outie.api.WithdrawalDomainController
import xyz.block.domainapi.util.Operation

class EligibilityControllerTest : BittyCityTestCase() {
  @Inject lateinit var subject: WithdrawalDomainController

  @Test
  fun `Fail when not eligible`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CheckingEligibility,
      walletAddress = Arbitrary.walletAddress.next(),
      amount = Arbitrary.bitcoins.next(),
    )
    eligibilityClient.nextEligibilityResult = Eligibility.Ineligible(emptyList()).success()

    subject.execute(withdrawal, emptyList(), Operation.EXECUTE).getOrThrow()

    withdrawalStore.getWithdrawalByToken(withdrawal.id).getOrThrow() should {
      it.state shouldBe Failed
      it.failureReason shouldBe CUSTOMER_IS_INELIGIBLE
    }
  }

  @Test
  fun `continue if eligible`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CheckingEligibility,
      walletAddress = Arbitrary.walletAddress.next(),
      amount = Arbitrary.bitcoins.next(),
      withdrawalSpeed = WithdrawalSpeed.RUSH,
    )
    eligibilityClient.nextEligibilityResult = Eligibility.Eligible(emptyList()).success()

    subject.execute(withdrawal, emptyList(), Operation.EXECUTE).getOrThrow()

    withdrawalStore.getWithdrawalByToken(withdrawal.id).getOrThrow()
      .state shouldBe WaitingForPendingConfirmationStatus
  }
}
