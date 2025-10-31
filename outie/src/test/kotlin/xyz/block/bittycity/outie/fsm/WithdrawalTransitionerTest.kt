package xyz.block.bittycity.outie.fsm

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.outie.jooq.generated.tables.references.WITHDRAWALS
import xyz.block.bittycity.outie.jooq.generated.tables.references.WITHDRAWAL_EVENTS
import xyz.block.bittycity.outie.models.CheckingSanctions
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.BittyCityTestCase

class WithdrawalTransitionerTest : BittyCityTestCase() {

  @Inject lateinit var withdrawalTransitioner: WithdrawalTransitioner

  @Test
  fun `create records withdrawal event`() = runTest {
    // Given a withdrawal in the database
    data.seedWithdrawal()

    val withdrawal = dslContext.selectFrom(WITHDRAWALS).fetchOne()
    withdrawal.shouldNotBeNull()
    withdrawal.get(WITHDRAWALS.STATE) shouldBe CollectingInfo.name
    withdrawal.get(WITHDRAWALS.VERSION)?.toLong() shouldBe 0L

    val event = dslContext.selectFrom(WITHDRAWAL_EVENTS).fetchOne()
    event.shouldNotBeNull()
    event.withdrawalId shouldBe withdrawal.get(WITHDRAWALS.ID)!!.toLong()
    event.fromState shouldBe null
    event.toState shouldBe CollectingInfo.name
    event.isProcessed shouldBe 0
  }

  @Test
  fun `persist records withdrawal event`() = runTest {
    // Given a withdrawal in the database
    val withdrawal = data.seedWithdrawal()

    // When persisting the transition
    withdrawalTransitioner.persist(
      CollectingInfo,
      withdrawal,
      CompleteInformation(ledgerClient)
    ).getOrThrow()

    val withdrawalFound = dslContext.selectFrom(WITHDRAWALS).fetchOne().shouldNotBeNull()
    withdrawalFound.get(WITHDRAWALS.STATE) shouldBe CheckingSanctions.name
    withdrawalFound.get(WITHDRAWALS.VERSION)?.toLong() shouldBe 1L

    val eventFound = dslContext.selectFrom(WITHDRAWAL_EVENTS).fetch()
      .drop(1).first().shouldNotBeNull()
    eventFound.withdrawalId shouldBe withdrawalFound.get(WITHDRAWALS.ID)!!.toLong()
    eventFound.fromState shouldBe CollectingInfo.name
    eventFound.toState shouldBe CheckingSanctions.name
    eventFound.isProcessed shouldBe 0
  }

  @Test
  fun `persist fails when withdrawal not found`() = runTest {
    // Given a non-existent withdrawal
    val nonExistentWithdrawal = Arbitrary.withdrawal.next()
    val transition = CompleteInformation(ledgerClient)

    // When persisting the transition
    val result = withdrawalTransitioner.persist(
      CollectingInfo,
      nonExistentWithdrawal,
      transition
    )

    // Then the operation should fail
    result.shouldBeFailure()
      .cause?.message shouldBe "Withdrawal not present: ${nonExistentWithdrawal.id}"
  }
}
