package xyz.block.bittycity.outie.jooq

import arrow.core.raise.result
import com.squareup.moshi.Moshi
import org.jooq.DSLContext
import xyz.block.bittycity.outie.jooq.generated.tables.records.WithdrawalEventsRecord
import xyz.block.bittycity.outie.jooq.generated.tables.references.WITHDRAWALS
import xyz.block.bittycity.outie.jooq.generated.tables.references.WITHDRAWAL_EVENTS
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.models.WithdrawalTransitionEvent
import xyz.block.bittycity.outie.store.WithdrawalEventOperations
import java.time.ZoneOffset
import org.jooq.JSON
import org.jooq.types.ULong

class JooqWithdrawalEventOperations(
  val context: DSLContext,
  val moshi: Moshi,
  ) : WithdrawalEventOperations {

  override fun insertWithdrawalEvent(
      withdrawalToken: WithdrawalToken,
      fromState: WithdrawalState?,
      toState: WithdrawalState,
      withdrawalSnapshot: Withdrawal,
  ): Result<WithdrawalTransitionEvent> = result {
    val withdrawalId = context
      .select(WITHDRAWALS.ID)
      .from(WITHDRAWALS)
      .where(WITHDRAWALS.TOKEN.eq(withdrawalToken.toString()))
      .fetchOne(WITHDRAWALS.ID)
      ?: raise(IllegalStateException("Withdrawal not found: $withdrawalToken"))

    val withdrawalJsonString = moshi.adapter(Withdrawal::class.java).toJson(withdrawalSnapshot)
    val insertStep = context.insertInto(WITHDRAWAL_EVENTS)
      .set(WITHDRAWAL_EVENTS.WITHDRAWAL_ID, withdrawalId.toLong())
      .set(WITHDRAWAL_EVENTS.FROM_STATE, fromState?.name)
      .set(WITHDRAWAL_EVENTS.TO_STATE, toState.name)
      .set(WITHDRAWAL_EVENTS.IS_PROCESSED, 0)
      .set(WITHDRAWAL_EVENTS.WITHDRAWAL_SNAPSHOT, JSON.valueOf(withdrawalJsonString))

    val insertedRecord = insertStep
      .returning(withdrawalEventFields)
      .fetchOne()!!

    insertedRecord.toModel().bind()
  }

  override fun fetchUnprocessedEvents(batchSize: Int): Result<List<WithdrawalTransitionEvent>> = result {
    require(batchSize > 0) { "Batch size must be positive" }

    val records = context
      .select(withdrawalEventFields)
      .from(WITHDRAWAL_EVENTS)
      .where(WITHDRAWAL_EVENTS.IS_PROCESSED.eq(0))
      .orderBy(WITHDRAWAL_EVENTS.ID.asc())
      .limit(batchSize)
      .fetch()

    records.mapNotNull { record ->
      record.into(WITHDRAWAL_EVENTS).toModel().getOrElse {
        markEventAsProcessed(record.get(WITHDRAWAL_EVENTS.ID)!!.toLong()).bind()
        null
      }
    }
  }

  override fun markEventAsProcessed(eventId: Long): Result<Unit> = result {
    val updatedCount = context
      .update(WITHDRAWAL_EVENTS)
      .set(WITHDRAWAL_EVENTS.IS_PROCESSED, 1)
      .where(WITHDRAWAL_EVENTS.ID.eq(ULong.valueOf(eventId)))
      .execute()

    if (updatedCount == 0) {
      raise(IllegalStateException("Withdrawal event not found: $eventId"))
    }
  }

  override fun fetchPreviousEvent(
    currentEvent: WithdrawalTransitionEvent
  ): Result<WithdrawalTransitionEvent?> = result {
    val record = context
      .select(withdrawalEventFields)
      .from(WITHDRAWAL_EVENTS)
      .where(WITHDRAWAL_EVENTS.WITHDRAWAL_ID.eq(currentEvent.withdrawalId))
      .and(WITHDRAWAL_EVENTS.ID.lessThan(ULong.valueOf(currentEvent.id)))
      .orderBy(WITHDRAWAL_EVENTS.ID.desc())
      .limit(1)
      .fetchOne()

    record?.into(WITHDRAWAL_EVENTS)?.toModel()?.bind()
  }

  companion object {
    private fun WithdrawalEventsRecord.toModel(): Result<WithdrawalTransitionEvent> = result {
      WithdrawalTransitionEvent(
        id = get(WITHDRAWAL_EVENTS.ID)!!.toLong(),
        createdAt = get(WITHDRAWAL_EVENTS.CREATED_AT)!!.toInstant(ZoneOffset.UTC),
        updatedAt = get(WITHDRAWAL_EVENTS.UPDATED_AT)!!.toInstant(ZoneOffset.UTC),
        version = get(WITHDRAWAL_EVENTS.VERSION)!!.toLong(),
        withdrawalId = get(WITHDRAWAL_EVENTS.WITHDRAWAL_ID)!!,
        from = get<String>(WITHDRAWAL_EVENTS.FROM_STATE)?.let { WithdrawalState.byName(it).bind() },
        to = WithdrawalState.byName(get<String>(WITHDRAWAL_EVENTS.TO_STATE)).bind(),
        isProcessed = get(WITHDRAWAL_EVENTS.IS_PROCESSED)!!.toInt() == 1,
        withdrawalSnapshot = get(WITHDRAWAL_EVENTS.WITHDRAWAL_SNAPSHOT)?.toString(),
      )
    }

    val withdrawalEventFields = listOf(
      WITHDRAWAL_EVENTS.ID,
      WITHDRAWAL_EVENTS.CREATED_AT,
      WITHDRAWAL_EVENTS.UPDATED_AT,
      WITHDRAWAL_EVENTS.VERSION,
      WITHDRAWAL_EVENTS.WITHDRAWAL_ID,
      WITHDRAWAL_EVENTS.FROM_STATE,
      WITHDRAWAL_EVENTS.TO_STATE,
      WITHDRAWAL_EVENTS.IS_PROCESSED,
      WITHDRAWAL_EVENTS.WITHDRAWAL_SNAPSHOT,
    )
  }
}
