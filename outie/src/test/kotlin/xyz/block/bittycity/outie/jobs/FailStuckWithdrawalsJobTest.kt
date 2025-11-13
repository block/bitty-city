package xyz.block.bittycity.outie.jobs

import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.outie.models.CollectingSelfAttestation
import xyz.block.bittycity.outie.models.Failed
import xyz.block.bittycity.outie.models.FailureReason
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

class FailStuckWithdrawalsJobTest : BittyCityTestCase() {
  @Inject lateinit var failStuckWithdrawalsJob: FailStuckWithdrawalsJob

  @Test
  fun `fail stuck withdrawals job does fails stuck withdrawals if log only is false`() = runTest {
    // Older than an hour and collection info - it's stuck
    val stuckWithdrawal = data.seedWithdrawal(
      id = Arbitrary.withdrawalToken.next(),
      state = CollectingInfo,
      withdrawalSpeed = WithdrawalSpeed.RUSH,
      walletAddress = walletAddress.next(),
      amount = amount.next(),
      updatedAt = clock.instant().minus(Duration.ofMinutes(61)),
    )
    // Collecting self attestation for 30 minutes - still not stuck
    val stillNotStuckWithdrawal = data.seedWithdrawal(
      id = Arbitrary.withdrawalToken.next(),
      state = CollectingSelfAttestation,
      withdrawalSpeed = WithdrawalSpeed.RUSH,
      walletAddress = walletAddress.next(),
      amount = amount.next(),
      updatedAt = clock.instant().minus(Duration.ofMinutes(30)),
    )
    failStuckWithdrawalsJob.execute(false)

    withdrawalStore.getWithdrawalByToken(stuckWithdrawal.id).getOrThrow() should {
      it.state shouldBe Failed
      it.failureReason shouldBe FailureReason.CUSTOMER_ABANDONED
    }

    withdrawalStore.getWithdrawalByToken(stillNotStuckWithdrawal.id).getOrThrow()
      .state shouldBe CollectingSelfAttestation
  }

  @Test
  fun `fail stuck withdrawals job doesn't fail stuck withdrawals if log only is true`() = runTest {
    // Older than an hour and collection info - it's stuck
    val stuckWithdrawal = data.seedWithdrawal(
      id = Arbitrary.withdrawalToken.next(),
      state = CollectingInfo,
      withdrawalSpeed = WithdrawalSpeed.RUSH,
      walletAddress = walletAddress.next(),
      amount = amount.next(),
      updatedAt = Instant.now(clock).minus(Duration.ofMinutes(61)),
    )
    // Collecting self attestation for 30 minutes - still not stuck
    val stillNotStuckWithdrawal = data.seedWithdrawal(
      id = Arbitrary.withdrawalToken.next(),
      state = CollectingSelfAttestation,
      withdrawalSpeed = WithdrawalSpeed.RUSH,
      walletAddress = walletAddress.next(),
      amount = amount.next(),
      updatedAt = Instant.now(clock).minus(Duration.ofMinutes(30)),
    )
    failStuckWithdrawalsJob.execute(logOnly = true)

    withdrawalStore.getWithdrawalByToken(stuckWithdrawal.id).getOrThrow()
      .state shouldBe CollectingInfo

    withdrawalStore.getWithdrawalByToken(stillNotStuckWithdrawal.id).getOrThrow()
      .state shouldBe CollectingSelfAttestation
  }
}
