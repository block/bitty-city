package xyz.block.bittycity.outie.jooq

import app.cash.kfsm.EffectPayload
import app.cash.kfsm.OutboxMessage
import app.cash.kfsm.OutboxStatus
import app.cash.kfsm.annotations.ExperimentalLibraryApi
import arrow.core.raise.result
import xyz.block.bittycity.outie.store.OutboxOperations
import xyz.block.bittycity.outie.jooq.generated.tables.references.OUTBOX
import com.squareup.moshi.Moshi
import org.jooq.DSLContext
import org.jooq.JSON

class JooqOutboxOperations(
  private val dsl: DSLContext,
  moshi: Moshi
) : OutboxOperations {

  private val effectPayloadAdapter = moshi.adapter(EffectPayload::class.java)

  @OptIn(ExperimentalLibraryApi::class)
  override fun insertOutboxMessage(message: OutboxMessage<String>): Result<Unit> = result {
    dsl.insertInto(OUTBOX)
      .set(OUTBOX.ID, message.id)
      .set(OUTBOX.VALUE_ID, message.valueId)
      .set(OUTBOX.EFFECT_TYPE, message.effectPayload.effectType)
      .set(OUTBOX.EFFECT_PAYLOAD, JSON.json(effectPayloadAdapter.toJson(message.effectPayload)))
      .set(OUTBOX.CREATED_AT, message.createdAt)
      .set(OUTBOX.STATUS, message.status.name)
      .set(OUTBOX.ATTEMPT_COUNT, message.attemptCount)
      .execute()
    Unit
  }

  @OptIn(ExperimentalLibraryApi::class)
  override fun fetchPendingMessages(limit: Int): Result<List<OutboxMessage<String>>> = result {
    dsl.selectFrom(OUTBOX)
      .where(OUTBOX.STATUS.eq(OutboxStatus.PENDING.name))
      .orderBy(OUTBOX.CREATED_AT.asc())
      .limit(limit)
      .fetch { record ->
        val effectPayload = effectPayloadAdapter.fromJson(record.effectPayload.data())!!
        OutboxMessage(
          id = record.id,
          valueId = record.valueId,
          effectPayload = effectPayload,
          createdAt = record.createdAt,
          processedAt = record.processedAt,
          status = OutboxStatus.valueOf(record.status),
          attemptCount = record.attemptCount ?: 0,
          lastError = record.lastError
        )
      }
  }

  @OptIn(ExperimentalLibraryApi::class)
  override fun markAsProcessed(id: String): Result<Unit> = result {
    dsl.update(OUTBOX)
      .set(OUTBOX.STATUS, OutboxStatus.COMPLETED.name)
      .set(OUTBOX.PROCESSED_AT, System.currentTimeMillis())
      .where(OUTBOX.ID.eq(id))
      .execute()
    Unit
  }

  @OptIn(ExperimentalLibraryApi::class)
  override fun markAsFailed(id: String, error: String?): Result<Unit> = result {
    dsl.update(OUTBOX)
      .set(OUTBOX.STATUS, OutboxStatus.FAILED.name)
      .set(OUTBOX.LAST_ERROR, error)
      .set(OUTBOX.ATTEMPT_COUNT, OUTBOX.ATTEMPT_COUNT.plus(1))
      .where(OUTBOX.ID.eq(id))
      .execute()
    Unit
  }

  @OptIn(ExperimentalLibraryApi::class)
  override fun fetchPreviousOutboxMessage(message: OutboxMessage<String>): Result<OutboxMessage<String>?> = result {
    dsl.selectFrom(OUTBOX)
      .where(OUTBOX.VALUE_ID.eq(message.valueId))
      .and(OUTBOX.CREATED_AT.lt(message.createdAt))
      .orderBy(OUTBOX.CREATED_AT.desc())
      .limit(1)
      .fetchOne { record ->
        val effectPayload = effectPayloadAdapter.fromJson(record.effectPayload.data())!!
        OutboxMessage(
          id = record.id,
          valueId = record.valueId,
          effectPayload = effectPayload,
          createdAt = record.createdAt,
          processedAt = record.processedAt,
          status = OutboxStatus.valueOf(record.status),
          attemptCount = record.attemptCount ?: 0,
          lastError = record.lastError
        )
      }
  }
}
