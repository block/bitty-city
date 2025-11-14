package xyz.block.bittycity.outie.controllers

import app.cash.quiver.extensions.success
import xyz.block.bittycity.outie.client.RiskEvaluation
import xyz.block.bittycity.outie.models.CheckingRisk
import xyz.block.bittycity.outie.models.CollectingScamWarningDecision
import xyz.block.bittycity.outie.models.Failed
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.WaitingForPendingConfirmationStatus
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.outie.api.WithdrawalDomainController
import xyz.block.domainapi.util.Operation

class RiskControllerTest : BittyCityTestCase() {

  @Inject lateinit var subject: WithdrawalDomainController

  @Test
  fun `Fail with RISK_BLOCKED`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CheckingRisk,
      walletAddress = Arbitrary.walletAddress.next(),
      amount = Arbitrary.bitcoins.next(),
    )
    riskService.nextRiskResult = RiskEvaluation.Blocked().success()

    subject.execute(
      withdrawal,
      emptyList(),
      Operation.EXECUTE
    ).shouldBeFailure<RiskBlocked>()

    withdrawalWithToken(withdrawal.id) should {
      it.state shouldBe Failed
      it.failureReason shouldBe FailureReason.RISK_BLOCKED
    }
  }

  @Test
  fun `Wait for scam warning approval`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CheckingRisk,
      walletAddress = Arbitrary.walletAddress.next(),
      amount = Arbitrary.bitcoins.next(),
    )
    riskService.nextRiskResult = RiskEvaluation.ActiveScamWarning().success()

    subject.execute(withdrawal, emptyList(), Operation.EXECUTE).getOrThrow()

    withdrawalWithToken(withdrawal.id)
      .state shouldBe CollectingScamWarningDecision
  }

  @Test
  fun `Continue after risk check is ok`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CheckingRisk,
      walletAddress = Arbitrary.walletAddress.next(),
      amount = Arbitrary.bitcoins.next(),
      withdrawalSpeed = WithdrawalSpeed.RUSH
    )

    subject.execute(withdrawal, emptyList(), Operation.EXECUTE).getOrThrow()

    withdrawalWithToken(withdrawal.id)
      .state shouldBe WaitingForPendingConfirmationStatus
  }
}
