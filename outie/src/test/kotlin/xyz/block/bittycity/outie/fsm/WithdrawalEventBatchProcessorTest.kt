package xyz.block.bittycity.outie.fsm

import xyz.block.bittycity.outie.models.CheckingRisk
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.outie.models.SubmittingOnChain
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.property.arbitrary.next
import org.junit.jupiter.api.Test

class WithdrawalEventBatchProcessorTest : BittyCityTestCase() {

  @Test
  fun `processes events successfully when events are available`() = runTest {
    // Given some unprocessed events in the database
    val withdrawal1 = data.seedWithdrawal(id = Arbitrary.withdrawalToken.next())
    val withdrawal2 = data.seedWithdrawal(id = Arbitrary.withdrawalToken.next())

    withdrawalTransactor.transact("Insert event 1") {
      insertWithdrawalEvent(
        withdrawalToken = withdrawal1.id,
        fromState = CollectingInfo,
        toState = CheckingRisk,
        withdrawalSnapshot = withdrawal1,
      )
    }.getOrThrow()

    withdrawalTransactor.transact("Insert event 2") {
      insertWithdrawalEvent(
        withdrawalToken = withdrawal2.id,
        fromState = CheckingRisk,
        toState = SubmittingOnChain,
        withdrawalSnapshot = withdrawal2,
      )
    }.getOrThrow()

    // When processing batches
    processWithdrawalEvents()

    // Events should be marked as processed in the database
    withdrawalTransactor.transactReadOnly("Verify events processed") {
      fetchUnprocessedEvents(10)
    }.getOrThrow().shouldBeEmpty()
  }

  @Test
  fun `processes multiple batches when batch size equals requested size`() = runTest {
    // Given 15 unprocessed events (more than one batch of 10)
    (1..15).map { data.seedWithdrawal(id = Arbitrary.withdrawalToken.next()) }
      .forEach { withdrawal ->
        withdrawalTransactor.transact("Insert event for ${withdrawal.id}") {
          insertWithdrawalEvent(
            withdrawalToken = withdrawal.id,
            fromState = CollectingInfo,
            toState = CheckingRisk,
            withdrawalSnapshot = withdrawal,
          )
        }.getOrThrow()
      }

    processWithdrawalEvents()

    // No events should remain unprocessed
    withdrawalTransactor.transactReadOnly("Verify all events processed") {
      fetchUnprocessedEvents(10)
    }.getOrThrow().shouldBeEmpty()
  }

  @Test
  fun `stops processing when batch size is less than requested size`() = runTest {
    // Given 7 unprocessed events (less than one full batch of 10)
    (1..7).map { data.seedWithdrawal(id = Arbitrary.withdrawalToken.next()) }
      .forEach { withdrawal ->
        withdrawalTransactor.transact("Insert event for ${withdrawal.id}") {
          insertWithdrawalEvent(
            withdrawalToken = withdrawal.id,
            fromState = CollectingInfo,
            toState = CheckingRisk,
            withdrawalSnapshot = withdrawal,
          )
        }.getOrThrow()
      }

    // When processing batches
    processWithdrawalEvents()

    // No events should remain unprocessed
    withdrawalTransactor.transactReadOnly("Verify all events processed") {
      fetchUnprocessedEvents(10)
    }.getOrThrow().shouldBeEmpty()
  }

  @Test
  fun `succeeds when no events are available`() = runTest {
    processWithdrawalEvents()
  }
}
