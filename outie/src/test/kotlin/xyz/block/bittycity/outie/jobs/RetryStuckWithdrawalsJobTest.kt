package xyz.block.bittycity.outie.jobs

import xyz.block.bittycity.outie.models.CheckingEligibility
import xyz.block.bittycity.outie.models.WaitingForPendingConfirmationStatus
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.Arbitrary.amount
import xyz.block.bittycity.outie.testing.Arbitrary.walletAddress
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class RetryStuckWithdrawalsJobTest : BittyCityTestCase() {

  @Inject lateinit var retryStuckWithdrawalsJob: RetryStuckWithdrawalsJob

  @Test
  fun `retry stuck withdrawals job does retry stuck withdrawals if log only is false`() = runTest {
    // Older than 5 minutes, and it's checking eligibility - it's stuck and retryable
    val stuckWithdrawal = data.seedWithdrawal(
      id = Arbitrary.withdrawalToken.next(),
      state = CheckingEligibility,
      withdrawalSpeed = WithdrawalSpeed.RUSH,
      walletAddress = walletAddress.next(),
      amount = amount.next(),
      updatedAt = clock.instant().minus(Duration.ofMinutes(6)),
    )

    // Checking eligibility for 30 seconds - still not stuck
    val stillNotStuckWithdrawal = data.seedWithdrawal(
      id = Arbitrary.withdrawalToken.next(),
      state = CheckingEligibility,
      withdrawalSpeed = WithdrawalSpeed.RUSH,
      walletAddress = walletAddress.next(),
      amount = amount.next(),
      updatedAt = clock.instant().minus(Duration.ofSeconds(30)),
    )

    retryStuckWithdrawalsJob.execute(false)

    withdrawalWithToken(stuckWithdrawal.id)
      .state shouldBe WaitingForPendingConfirmationStatus

    withdrawalWithToken(stillNotStuckWithdrawal.id)
      .state shouldBe CheckingEligibility
  }

  @Test
  fun `retry stuck withdrawals job doesn't retry stuck withdrawals if log only is true`() = runTest {
    // Older than an hour and collection info - it's stuck
    val stuckWithdrawal = data.seedWithdrawal(
      id = Arbitrary.withdrawalToken.next(),
      state = CheckingEligibility,
      withdrawalSpeed = WithdrawalSpeed.RUSH,
      walletAddress = walletAddress.next(),
      amount = amount.next(),
      updatedAt = Instant.now(clock).minus(Duration.ofMinutes(61)),
    )
    // Collecting self attestation for 30 minutes - still not stuck
    val stillNotStuckWithdrawal = data.seedWithdrawal(
      id = Arbitrary.withdrawalToken.next(),
      state = CheckingEligibility,
      withdrawalSpeed = WithdrawalSpeed.RUSH,
      walletAddress = walletAddress.next(),
      amount = amount.next(),
      updatedAt = Instant.now(clock).minus(Duration.ofMinutes(30)),
    )
    retryStuckWithdrawalsJob.execute(logOnly = true)

    withdrawalWithToken(stuckWithdrawal.id)
      .state shouldBe CheckingEligibility

    withdrawalWithToken(stillNotStuckWithdrawal.id)
      .state shouldBe CheckingEligibility
  }
}
