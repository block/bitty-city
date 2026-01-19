package xyz.block.bittycity.outie.controllers

import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.outie.api.WithdrawalDomainController
import xyz.block.bittycity.outie.models.Failed
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.util.Operation

class FailedControllerTest  : BittyCityTestCase() {

  @Inject lateinit var subject: WithdrawalDomainController

  @Test
  fun `Operating on a failed withdrawal returns an InvalidProcessState error`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = Failed,
      walletAddress = Arbitrary.walletAddress.next(),
      amount = Arbitrary.bitcoins.next(),
    ) { it.copy(failureReason = FailureReason.RISK_BLOCKED) }

    subject.execute(
      withdrawal,
      emptyList(),
      Operation.EXECUTE
    ).shouldBeFailure<DomainApiError.InvalidProcessState>()

    withdrawalWithToken(withdrawal.id) should {
      it.state shouldBe Failed
      it.failureReason shouldBe FailureReason.RISK_BLOCKED
    }
  }
}