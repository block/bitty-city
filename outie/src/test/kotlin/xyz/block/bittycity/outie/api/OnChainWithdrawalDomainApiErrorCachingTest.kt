package xyz.block.bittycity.outie.api

import xyz.block.bittycity.common.idempotency.CachedError
import xyz.block.bittycity.outie.models.HoldingSubmission
import xyz.block.bittycity.outie.models.ObservedInMempool
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.WaitingForConfirmedOnChainStatus
import xyz.block.bittycity.outie.models.WaitingForPendingConfirmationStatus
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beInstanceOf
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.domainapi.Input

/**
 * Tests that verify error caching behavior for withdrawals in different states.
 *
 * Background: During a production incident, transient errors in post-submission states
 * were cached, preventing retries and permanently blocking withdrawals. This test suite
 * ensures that errors in post-submission states (where bitcoin has already been sent) are
 * NOT cached, allowing retries to discover the real outcome.
 */
class OnChainWithdrawalDomainApiErrorCachingTest : BittyCityTestCase() {

  @Inject
  private lateinit var onChainWithdrawalDomainApi: OnChainWithdrawalDomainApi

  @Test
  fun `errors in WaitingForPendingConfirmationStatus are not cached`() = runTest {
    // Create a withdrawal in WaitingForPendingConfirmationStatus state
    val withdrawal = data.seedWithdrawal(
      state = WaitingForPendingConfirmationStatus,
      amount = Arbitrary.amount.next(),
      walletAddress = Arbitrary.walletAddress.next(),
    )

    // Configure the limit client to fail, simulating a transient error
    limitClient.failNextEvaluation()

    // First call should fail due to the simulated error
    val firstResult = onChainWithdrawalDomainApi.execute(
      withdrawal.id,
      emptyList<Input.HurdleResponse<RequirementId>>()
    )
    firstResult.shouldBeFailure()

    // Second call with the same parameters should NOT return a cached error
    // Instead, it should retry the operation
    // To verify this, we ensure the limit client is called again (not failing this time)
    limitClient.reset()

    // If the error was cached, this would return CachedError immediately
    // If NOT cached, it will retry and succeed (or fail differently)
    val secondResult = onChainWithdrawalDomainApi.execute(
      withdrawal.id,
      emptyList<Input.HurdleResponse<RequirementId>>()
    )

    // The result should be a success or a different error, NOT a CachedError
    // This proves the first error was not cached
    secondResult.exceptionOrNull() shouldNot beInstanceOf<CachedError>()
  }

  @Test
  fun `errors in WaitingForConfirmedOnChainStatus are not cached`() = runTest {
    // Create a withdrawal in WaitingForConfirmedOnChainStatus state
    val withdrawal = data.seedWithdrawal(
      state = WaitingForConfirmedOnChainStatus,
      amount = Arbitrary.amount.next(),
      walletAddress = Arbitrary.walletAddress.next(),
    )

    // Configure the limit client to fail, simulating a transient error
    limitClient.failNextEvaluation()

    // First call should fail due to the simulated error
    val firstResult = onChainWithdrawalDomainApi.execute(
      withdrawal.id,
      emptyList<Input.HurdleResponse<RequirementId>>()
    )
    firstResult.shouldBeFailure()

    // Second call with the same parameters should NOT return a cached error
    limitClient.reset()

    val secondResult = onChainWithdrawalDomainApi.execute(
      withdrawal.id,
      emptyList<Input.HurdleResponse<RequirementId>>()
    )

    // Should not be a CachedError - the error should not have been cached
    secondResult.exceptionOrNull() shouldNot beInstanceOf<CachedError>()
  }

  @Test
  fun `errors in pre-submission states are cached`() = runTest {
    // Create a withdrawal in HoldingSubmission state (pre-submission)
    val withdrawal = data.seedWithdrawal(
      state = HoldingSubmission,
      amount = Arbitrary.amount.next(),
      walletAddress = Arbitrary.walletAddress.next(),
    )

    // Configure the limit client to fail
    limitClient.failNextEvaluation()

    // First call should fail
    val firstResult = onChainWithdrawalDomainApi.execute(
      withdrawal.id,
      emptyList<Input.HurdleResponse<RequirementId>>()
    )
    firstResult.shouldBeFailure()

    // Reset the limit client so it would succeed on the next call
    limitClient.reset()

    // Second call should return the CACHED error, not retry
    val secondResult = onChainWithdrawalDomainApi.execute(
      withdrawal.id,
      emptyList<Input.HurdleResponse<RequirementId>>()
    )

    // For pre-submission states, the error should be cached
    // This means the second call returns a CachedError immediately
    secondResult.shouldBeFailure()
    secondResult.exceptionOrNull().shouldBeInstanceOf<CachedError>()
  }

  @Test
  fun `successes in post-submission states are cached`() = runTest {
    // Create a withdrawal in WaitingForPendingConfirmationStatus state
    val withdrawal = data.seedWithdrawal(
      state = WaitingForPendingConfirmationStatus,
      amount = Arbitrary.amount.next(),
      walletAddress = Arbitrary.walletAddress.next(),
    )

    val resumeResult = ObservedInMempool(
      Arbitrary.stringToken.next(),
      Arbitrary.outputIndex.next()
    )

    // First call should succeed
    val firstResult = onChainWithdrawalDomainApi.resume(
      withdrawal.id,
      resumeResult
    )
    firstResult.shouldBeSuccess()

    // Second call with same parameters should return cached success
    val secondResult = onChainWithdrawalDomainApi.resume(
      withdrawal.id,
      resumeResult
    )
    secondResult.shouldBeSuccess()

    // Verify it was actually cached by checking that the withdrawal state
    // only transitioned once (not twice)
    val finalWithdrawal = withdrawalWithToken(withdrawal.id)
    finalWithdrawal.state shouldBe WaitingForConfirmedOnChainStatus
  }

}
