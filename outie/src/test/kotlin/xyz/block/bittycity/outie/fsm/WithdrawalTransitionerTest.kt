package xyz.block.bittycity.outie.fsm

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.outie.models.CheckingSanctions
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.BittyCityTestCase

class WithdrawalTransitionerTest : BittyCityTestCase() {

  @Inject lateinit var withdrawalTransitioner: WithdrawalTransitioner

  @Test
  fun `create records withdrawal event`() = runTest {
    data.seedWithdrawal()

    val withdrawal = fakeWithdrawalOperations.getByToken(data.withdrawalToken).getOrThrow()
    withdrawal.shouldNotBeNull()
    withdrawal.state shouldBe CollectingInfo
    withdrawal.version shouldBe 1L

    val events = fakeWithdrawalOperations.fetchUnprocessedEvents(10).getOrThrow()
    events.size shouldBe 1
    val event = events.first()
    event.from shouldBe null
    event.to shouldBe CollectingInfo
    event.isProcessed shouldBe false
  }

  @Test
  fun `persist records withdrawal event`() = runTest {
    val withdrawal = data.seedWithdrawal()

    withdrawalTransitioner.persist(
      CollectingInfo,
      withdrawal,
      CompleteInformation(ledgerClient)
    ).getOrThrow()

    val withdrawalFound = fakeWithdrawalOperations.getByToken(data.withdrawalToken).getOrThrow()
    withdrawalFound.shouldNotBeNull()
    withdrawalFound.state shouldBe CheckingSanctions
    withdrawalFound.version shouldBe 2L

    val events = fakeWithdrawalOperations.fetchUnprocessedEvents(10).getOrThrow()
    events.size shouldBe 2
    val event = events.last()
    event.from shouldBe CollectingInfo
    event.to shouldBe CheckingSanctions
    event.isProcessed shouldBe false
  }

  @Test
  fun `persist fails when withdrawal not found`() = runTest {
    val nonExistentWithdrawal = Arbitrary.withdrawal.next()
    val transition = CompleteInformation(ledgerClient)

    val result = withdrawalTransitioner.persist(
      CollectingInfo,
      nonExistentWithdrawal,
      transition
    )

    result.shouldBeFailure()
      .message shouldBe "Withdrawal not present: ${nonExistentWithdrawal.id}"
  }
}
