package xyz.block.bittycity.outie.store

import xyz.block.bittycity.outie.jooq.JooqWithdrawalEntityOperations
import xyz.block.bittycity.outie.jooq.generated.tables.references.WITHDRAWALS
import xyz.block.bittycity.outie.jooq.generated.tables.references.WITHDRAWAL_EVENTS
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.outie.models.CheckingRisk
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.outie.models.SubmittingOnChain
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Test

class WithdrawalEventOperationsTest : BittyCityTestCase() {

  @Test
  fun `insert creates withdrawal event record`() = runTest {
    // Given a withdrawal in the database
    val withdrawal = data.seedWithdrawal()

    // When inserting a withdrawal event
    withdrawalTransactor.transact("Insert withdrawal event") {
      insertWithdrawalEvent(
        withdrawalToken = withdrawal.id,
        fromState = CollectingInfo,
        toState = SubmittingOnChain,
        withdrawalSnapshot = withdrawal,
      )
    }.getOrThrow()

    // Then the event should be stored correctly
    val withdrawalFound = dslContext.select(
      JooqWithdrawalEntityOperations.withdrawalFields
    ).from(WITHDRAWALS).fetchOne().shouldNotBeNull()
    val eventFound =
      dslContext.selectFrom(WITHDRAWAL_EVENTS).fetch().drop(1).first().shouldNotBeNull()

    eventFound.withdrawalId shouldBe withdrawalFound.get(WITHDRAWALS.ID)!!.toLong()
    eventFound.fromState shouldBe CollectingInfo.name
    eventFound.toState shouldBe SubmittingOnChain.name
    eventFound.isProcessed shouldBe 0
  }

  @Test
  fun `insert fails when withdrawal not found`() = runTest {
    // Given a non-existent withdrawal token
    val nonExistentToken = Arbitrary.withdrawalToken.next()

    // When inserting a withdrawal event
    val result = withdrawalTransactor.transact("Insert withdrawal event") {
      insertWithdrawalEvent(
        withdrawalToken = nonExistentToken,
        fromState = CollectingInfo,
        toState = SubmittingOnChain,
        withdrawalSnapshot = data.newWithdrawal.copy(id = nonExistentToken),
      )
    }

    // Then the operation should fail
    result.shouldBeFailure()
      .message shouldBe "Withdrawal not found: $nonExistentToken"
  }

  @Test
  fun `fetchUnprocessedEvents returns empty list when no unprocessed events exist`() = runTest {
    // When fetching unprocessed events with no events in database
    val result = withdrawalTransactor.transactReadOnly("Fetch unprocessed events") {
      fetchUnprocessedEvents(batchSize = 10)
    }

    // Then the result should be empty
    result.getOrThrow().shouldBeEmpty()
  }

  @Test
  fun `fetchUnprocessedEvents returns only unprocessed events`() = runTest {
    // Given multiple withdrawals with events
    val withdrawal1 = data.seedWithdrawal(id = Arbitrary.withdrawalToken.next())
    val withdrawal2 = data.seedWithdrawal(id = Arbitrary.withdrawalToken.next())
    processWithdrawalEvents()

    // Create unprocessed events
    val unprocessedEvent1 = withdrawalTransactor.transact("Insert unprocessed event 1") {
      insertWithdrawalEvent(
        withdrawalToken = withdrawal1.id,
        fromState = CollectingInfo,
        toState = CheckingRisk,
        withdrawalSnapshot = withdrawal1,
      )
    }.getOrThrow()

    val unprocessedEvent2 = withdrawalTransactor.transact("Insert unprocessed event 2") {
      insertWithdrawalEvent(
        withdrawalToken = withdrawal2.id,
        fromState = CheckingRisk,
        toState = SubmittingOnChain,
        withdrawalSnapshot = withdrawal2,
      )
    }.getOrThrow()

    // Mark one event as processed directly in database
    dslContext.update(WITHDRAWAL_EVENTS)
      .set(WITHDRAWAL_EVENTS.IS_PROCESSED, 1)
      .where(WITHDRAWAL_EVENTS.ID.eq(org.jooq.types.ULong.valueOf(unprocessedEvent1.id)))
      .execute()

    // When fetching unprocessed events
    val result = withdrawalTransactor.transactReadOnly("Fetch unprocessed events") {
      fetchUnprocessedEvents(batchSize = 10)
    }

    // Then only unprocessed events should be returned
    result.getOrThrow() should { events ->
      events shouldHaveSize 1
      events.first().id shouldBe unprocessedEvent2.id
      events.first().isProcessed shouldBe false
    }
  }

  @Test
  fun `fetchUnprocessedEvents respects batch size limit`() = runTest {
    // Given multiple withdrawals with unprocessed events
    val withdrawals = (1..5).map { data.seedWithdrawal(id = Arbitrary.withdrawalToken.next()) }

    // Create unprocessed events for each withdrawal
    withdrawals.forEach { withdrawal ->
      withdrawalTransactor.transact("Insert event for ${withdrawal.id}") {
        insertWithdrawalEvent(
          withdrawalToken = withdrawal.id,
          fromState = CollectingInfo,
          toState = CheckingRisk,
          withdrawalSnapshot = withdrawal,
        )
      }.getOrThrow()
    }

    // When fetching with batch size smaller than total events
    val result = withdrawalTransactor.transactReadOnly("Fetch unprocessed events") {
      fetchUnprocessedEvents(batchSize = 3)
    }

    // Then only batch size number of events should be returned
    result.getOrThrow() should { events ->
      events shouldHaveSize 3
      events.forEach { event ->
        event.isProcessed shouldBe false
      }
    }
  }

  @Test
  fun `fetchUnprocessedEvents orders by created_at ascending`() = runTest {
    // Given multiple withdrawals
    val withdrawal1 = data.seedWithdrawal(id = Arbitrary.withdrawalToken.next())
    val withdrawal2 = data.seedWithdrawal(id = Arbitrary.withdrawalToken.next())
    processWithdrawalEvents()

    // Create events (they will have different created_at timestamps)
    val event1 = withdrawalTransactor.transact("Insert first event") {
      insertWithdrawalEvent(
        withdrawalToken = withdrawal1.id,
        fromState = CollectingInfo,
        toState = CheckingRisk,
        withdrawalSnapshot = withdrawal1,
      )
    }.getOrThrow()

    val event2 = withdrawalTransactor.transact("Insert second event") {
      insertWithdrawalEvent(
        withdrawalToken = withdrawal2.id,
        fromState = CollectingInfo,
        toState = CheckingRisk,
        withdrawalSnapshot = withdrawal2,
      ).onSuccess { event ->
        // Update the created_at timestamp to ensure proper ordering test
        dslContext.update(WITHDRAWAL_EVENTS)
          .set(WITHDRAWAL_EVENTS.CREATED_AT,
            LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
          .where(WITHDRAWAL_EVENTS.ID.eq(org.jooq.types.ULong.valueOf(event.id)))
      }
    }.getOrThrow()

    // When fetching unprocessed events
    val result = withdrawalTransactor.transactReadOnly("Fetch unprocessed events") {
      fetchUnprocessedEvents(batchSize = 10)
    }

    result.getOrThrow() should { events ->
      events shouldHaveSize 2
      events[0].id shouldBe event1.id
      events[1].id shouldBe event2.id
    }
  }

  @Test
  fun `fetchUnprocessedEvents fails with non-positive batch size`() = runTest {
    // When fetching with zero batch size
    val resultZero = withdrawalTransactor.transactReadOnly("Fetch with zero batch size") {
      fetchUnprocessedEvents(batchSize = 0)
    }

    // Then the operation should fail
    resultZero.shouldBeFailure()

    // When fetching with negative batch size
    val resultNegative = withdrawalTransactor.transactReadOnly("Fetch with negative batch size") {
      fetchUnprocessedEvents(batchSize = -1)
    }

    // Then the operation should fail
    resultNegative.shouldBeFailure()
  }

  @Test
  fun `markEventAsProcessed successfully marks event as processed`() = runTest {
    // Given a withdrawal with an unprocessed event
    val withdrawal = data.seedWithdrawal()
    val event = withdrawalTransactor.transact("Insert withdrawal event") {
      insertWithdrawalEvent(
        withdrawalToken = withdrawal.id,
        fromState = CollectingInfo,
        toState = CheckingRisk,
        withdrawalSnapshot = withdrawal,
      )
    }.getOrThrow()

    // When marking the event as processed
    val result = withdrawalTransactor.transact("Mark event as processed") {
      markEventAsProcessed(event.id)
    }

    // Then the operation should succeed
    result.getOrThrow()

    // And the event should be marked as processed in the database
    dslContext.selectFrom(WITHDRAWAL_EVENTS)
      .where(WITHDRAWAL_EVENTS.ID.eq(org.jooq.types.ULong.valueOf(event.id)))
      .fetchOne().shouldNotBeNull().isProcessed shouldBe 1
  }

  @Test
  fun `markEventAsProcessed fails when event does not exist`() = runTest {
    // Given a non-existent event ID
    val nonExistentEventId = 999999L

    // When marking the non-existent event as processed
    val result = withdrawalTransactor.transact("Mark non-existent event as processed") {
      markEventAsProcessed(nonExistentEventId)
    }

    // Then the operation should fail
    result.shouldBeFailure()
      .message shouldBe "Withdrawal event not found: $nonExistentEventId"
  }

  @Test
  fun `markEventAsProcessed removes event from unprocessed list`() = runTest {
    // Given multiple withdrawals with unprocessed events
    val withdrawal1 = data.seedWithdrawal(id = Arbitrary.withdrawalToken.next())
    val withdrawal2 = data.seedWithdrawal(id = Arbitrary.withdrawalToken.next())
    processWithdrawalEvents()

    val event1 = withdrawalTransactor.transact("Insert event 1") {
      insertWithdrawalEvent(
        withdrawalToken = withdrawal1.id,
        fromState = CollectingInfo,
        toState = CheckingRisk,
        withdrawalSnapshot = withdrawal1,
      )
    }.getOrThrow()

    val event2 = withdrawalTransactor.transact("Insert event 2") {
      insertWithdrawalEvent(
        withdrawalToken = withdrawal2.id,
        fromState = CheckingRisk,
        toState = SubmittingOnChain,
        withdrawalSnapshot = withdrawal2,
      )
    }.getOrThrow()

    // When marking one event as processed
    withdrawalTransactor.transact("Mark event 1 as processed") {
      markEventAsProcessed(event1.id)
    }.getOrThrow()

    // Then only the unprocessed event should be returned
    val unprocessedEvents = withdrawalTransactor.transactReadOnly("Fetch unprocessed events") {
      fetchUnprocessedEvents(batchSize = 10)
    }.getOrThrow()

    unprocessedEvents should { events ->
      events shouldHaveSize 1
      events.first().id shouldBe event2.id
      events.first().isProcessed shouldBe false
    }
  }

  @Test
  fun `insert creates withdrawal event record with withdrawal snapshot JSON`() = runTest {
    // Given a withdrawal in the database
    val withdrawal = data.seedWithdrawal()
    val testAddress = Arbitrary.walletAddress.next()
    val snapshotWithdrawal = withdrawal.copy(
      amount = Bitcoins(1000),
      note = "test note",
      targetWalletAddress = testAddress
    )

    // When inserting a withdrawal event with withdrawal snapshot
    val event = withdrawalTransactor.transact("Insert withdrawal event with withdrawal snapshot") {
      insertWithdrawalEvent(
        withdrawalToken = withdrawal.id,
        fromState = CollectingInfo,
        toState = CheckingRisk,
        withdrawalSnapshot = snapshotWithdrawal,
      )
    }.getOrThrow()

    // Then the event should be stored correctly with withdrawal snapshot (field order may vary)
    event.withdrawalSnapshot.shouldNotBeNull() should { json ->
      json.contains("\"id\"") shouldBe true
      json.contains(withdrawal.id.toString()) shouldBe true
      json.contains("\"amount\"") shouldBe true
      json.contains("1000") shouldBe true
      json.contains("\"targetWalletAddress\"") shouldBe true
      json.contains(testAddress.toString()) shouldBe true
      json.contains("\"note\"") shouldBe true
      json.contains("test note") shouldBe true
      json.contains("\"state\"") shouldBe true
      json.contains("COLLECTING_INFO") shouldBe true
    }

    // And in the database
    val withdrawalRecord = dslContext.select(JooqWithdrawalEntityOperations.withdrawalFields)
      .from(WITHDRAWALS)
      .where(WITHDRAWALS.TOKEN.eq(withdrawal.id.toString()))
      .fetchOne().shouldNotBeNull()
    val eventRecord = dslContext.selectFrom(WITHDRAWAL_EVENTS)
      .where(WITHDRAWAL_EVENTS.ID.eq(org.jooq.types.ULong.valueOf(event.id)))
      .fetchOne().shouldNotBeNull()

    eventRecord.withdrawalId shouldBe withdrawalRecord.get(WITHDRAWALS.ID)!!.toLong()
    eventRecord.fromState shouldBe CollectingInfo.name
    eventRecord.toState shouldBe CheckingRisk.name
    val jsonString = eventRecord.withdrawalSnapshot?.toString()
    jsonString.shouldNotBeNull()
    jsonString should { json ->
      json.contains("\"id\"") shouldBe true
      json.contains(withdrawal.id.toString()) shouldBe true
      json.contains("\"amount\"") shouldBe true
      json.contains("1000") shouldBe true
      json.contains("\"targetWalletAddress\"") shouldBe true
      json.contains(testAddress.toString()) shouldBe true
      json.contains("\"note\"") shouldBe true
      json.contains("test note") shouldBe true
      json.contains("\"state\"") shouldBe true
      json.contains("COLLECTING_INFO") shouldBe true
    }
  }

  @Test
  fun `insert creates withdrawal event record with complete withdrawal snapshot JSON`() = runTest {
    val withdrawal = data.seedWithdrawal()
    val testAddress = Arbitrary.walletAddress.next()
    // Create a more complete withdrawal snapshot
    val snapshotWithdrawal = withdrawal.copy(
      amount = Bitcoins(1000),
      note = "Updated note",
      state = CheckingRisk,
      targetWalletAddress = testAddress
    )

    val result = withdrawalTransactor.transact("Insert event with complete withdrawal snapshot") {
      insertWithdrawalEvent(
        withdrawalToken = withdrawal.id,
        fromState = CollectingInfo,
        toState = CheckingRisk,
        withdrawalSnapshot = snapshotWithdrawal
      )
    }

    val event = result.getOrThrow()

    // Verify the withdrawal snapshot is stored correctly (field order may vary)
    event.withdrawalSnapshot.shouldNotBeNull() should { json ->
      json.contains("\"id\"") shouldBe true
      json.contains(withdrawal.id.toString()) shouldBe true
      json.contains("\"customerId\"") shouldBe true
      json.contains(withdrawal.customerId.toString()) shouldBe true
      json.contains("\"amount\"") shouldBe true
      json.contains("1000") shouldBe true
      json.contains("\"note\"") shouldBe true
      json.contains("Updated note") shouldBe true
      json.contains("\"targetWalletAddress\"") shouldBe true
      json.contains(testAddress.toString()) shouldBe true
      json.contains("\"state\"") shouldBe true
      json.contains("CHECKING_RISK") shouldBe true
      json.contains("\"sourceBalanceToken\"") shouldBe true
      json.contains(withdrawal.sourceBalanceToken.toString()) shouldBe true
    }
  }
}
