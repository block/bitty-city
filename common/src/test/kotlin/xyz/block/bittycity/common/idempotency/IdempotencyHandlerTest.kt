package xyz.block.bittycity.common.idempotency

import arrow.core.raise.result
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import xyz.block.bittycity.common.store.Operations
import xyz.block.bittycity.common.store.Transactor
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.ExecuteResponse
import xyz.block.domainapi.Input

class IdempotencyHandlerTest {
  private val operations = InMemoryIdempotencyOperations()
  private val handler = IdempotencyHandler(
    moshi = Moshi.Builder().build(),
    transactor = PassThroughTransactor(operations),
    inputsAdapter = { TestInputsJsonAdapter() },
    resumeInputsAdapter = { TestResumeInputsJsonAdapter() }
  )

  @Test
  fun `clearCachedResponses removes every cached response for the id and returns the count`() {
    val id = TestId("one")
    val firstKey = handler.handle(id, 1, emptyList()).getOrThrow().leftOrNull().shouldNotBeNull()
    handler.handle(id, 2, emptyList()).getOrThrow().leftOrNull().shouldNotBeNull()
    handler.updateCachedResponse(
      firstKey,
      id,
      Result.failure(RuntimeException("boom"))
    ).getOrThrow()

    handler.clearCachedResponses(id).getOrThrow() shouldBe 2
    operations.responsesFor(id) shouldBe 0
  }

  @Test
  fun `clearCachedResponses leaves other ids untouched`() {
    val id1 = TestId("one")
    val id2 = TestId("two")
    handler.handle(id1, 1, emptyList()).getOrThrow()
    handler.handle(id2, 1, emptyList()).getOrThrow()

    handler.clearCachedResponses(id1).getOrThrow() shouldBe 1
    handler.handle(id2, 1, emptyList())
      .shouldBeFailure(DomainApiError.AlreadyProcessing(id2.toString()))
  }

  @Test
  fun `clearCachedResponses returns 0 when nothing is cached`() {
    handler.clearCachedResponses(TestId("empty")).getOrThrow() shouldBe 0
  }

  @Test
  fun `handle runs again with the same inputs after clearCachedResponses removes a cached error`() {
    val id = TestId("one")
    val key = handler.handle(id, 1, emptyList()).getOrThrow().leftOrNull().shouldNotBeNull()
    handler.updateCachedResponse(key, id, Result.failure(RuntimeException("boom"))).getOrThrow()

    handler.handle(id, 1, emptyList()).shouldBeFailure<CachedError>()
    handler.clearCachedResponses(id).getOrThrow() shouldBe 1
    handler.handle(id, 1, emptyList()).getOrThrow().leftOrNull() shouldBe key
  }
}

private data class TestId(val value: String)

private class InMemoryIdempotencyOperations : IdempotencyOperations<TestId, String> {
  private data class CompositeKey(
    val idempotencyKey: String,
    val requestId: TestId
  )

  private val responses = mutableMapOf<CompositeKey, IdempotentResponse<TestId, String>>()

  override fun findResponse(
    idempotencyKey: String,
    requestId: TestId
  ): Result<IdempotentResponse<TestId, String>?> = result {
    responses[CompositeKey(idempotencyKey, requestId)]
  }

  override fun insertResponse(
    response: IdempotentResponse<TestId, String>
  ): Result<IdempotentResponse<TestId, String>> = result {
    val key = CompositeKey(response.idempotencyKey, response.requestId)
    if (responses.containsKey(key)) {
      raise(AlreadyProcessingException(null))
    }
    val inserted = response.copy(version = 1)
    responses[key] = inserted
    inserted
  }

  override fun updateResponse(
    idempotencyKey: String,
    response: IdempotentResponse<TestId, String>
  ): Result<IdempotentResponse<TestId, String>> = result {
    val key = CompositeKey(idempotencyKey, response.requestId)
    val existing = responses[key] ?: raise(ResponseNotPresent(idempotencyKey))
    if (existing.version != response.version) {
      raise(ResponseVersionMismatch(response.version, response.requestId.toString()))
    }
    val updated = response.copy(version = response.version + 1)
    responses[key] = updated
    updated
  }

  override fun deleteResponse(idempotencyKey: String, requestId: TestId): Result<Unit> = result {
    responses.remove(CompositeKey(idempotencyKey, requestId))
  }

  override fun deleteResponsesForRequest(requestId: TestId): Result<Int> = result {
    val keys = responses.keys.filter { it.requestId == requestId }
    keys.forEach { responses.remove(it) }
    keys.size
  }

  fun responsesFor(id: TestId): Int = responses.keys.count { it.requestId == id }
}

private class PassThroughTransactor<O : Operations>(
  private val operations: O
) : Transactor<O> {
  override fun <T> transact(comment: String, block: O.() -> Result<T>): Result<T> =
    operations.block()

  override fun <T> transactReadOnly(comment: String, block: O.() -> Result<T>): Result<T> =
    operations.block()
}

private class TestInputsJsonAdapter : JsonAdapter<IdempotentInputs<TestId, String>>() {
  override fun toJson(writer: JsonWriter, value: IdempotentInputs<TestId, String>?) {
    value.shouldNotBeNull()
    writer.value("${value.id.value}|${value.backCounter}|${value.hurdleResponses.size}")
  }

  override fun fromJson(reader: JsonReader): IdempotentInputs<TestId, String> =
    throw UnsupportedOperationException()
}

private class TestResumeInputsJsonAdapter : JsonAdapter<IdempotentResumeInputs<TestId, String>>() {
  override fun toJson(writer: JsonWriter, value: IdempotentResumeInputs<TestId, String>?) {
    value.shouldNotBeNull()
    writer.value("${value.id.value}|${value.resumeResult}")
  }

  override fun fromJson(reader: JsonReader): IdempotentResumeInputs<TestId, String> =
    throw UnsupportedOperationException()
}
