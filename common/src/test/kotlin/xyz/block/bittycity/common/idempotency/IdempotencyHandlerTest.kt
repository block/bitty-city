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
  fun `clearCachedErrors removes errors and keeps successes and placeholders for the id`() {
    val id = TestId("one")
    val errorKey = handler.handle(id, 1, emptyList()).getOrThrow().leftOrNull().shouldNotBeNull()
    val successKey = handler.handle(id, 2, emptyList()).getOrThrow().leftOrNull().shouldNotBeNull()
    handler.handle(id, 3, emptyList()).getOrThrow().leftOrNull().shouldNotBeNull()
    val cachedSuccess = ExecuteResponse<TestId, String>(
      id = id,
      interactions = emptyList(),
      nextEndpoint = null
    )
    handler.updateCachedResponse(
      errorKey,
      id,
      Result.failure(RuntimeException("boom"))
    ).getOrThrow()
    handler.updateCachedResponse(successKey, id, Result.success(cachedSuccess)).getOrThrow()

    handler.clearCachedErrors(id).getOrThrow() shouldBe 1
    operations.responsesFor(id) shouldBe 2
    handler.handle(id, 2, emptyList()).getOrThrow().fold({ null }, { it }) shouldBe cachedSuccess
    handler.handle(id, 3, emptyList())
      .shouldBeFailure(DomainApiError.AlreadyProcessing(id.toString()))
  }

  @Test
  fun `clearCachedErrors leaves other ids untouched`() {
    val id1 = TestId("one")
    val id2 = TestId("two")
    val key = handler.handle(id1, 1, emptyList()).getOrThrow().leftOrNull().shouldNotBeNull()
    handler.updateCachedResponse(key, id1, Result.failure(RuntimeException("boom"))).getOrThrow()
    handler.handle(id2, 1, emptyList()).getOrThrow()

    handler.clearCachedErrors(id1).getOrThrow() shouldBe 1
    handler.handle(id2, 1, emptyList())
      .shouldBeFailure(DomainApiError.AlreadyProcessing(id2.toString()))
  }

  @Test
  fun `clearCachedErrors returns 0 when nothing is cached`() {
    handler.clearCachedErrors(TestId("empty")).getOrThrow() shouldBe 0
  }

  @Test
  fun `handle runs again with the same inputs after clearCachedErrors removes a cached error`() {
    val id = TestId("one")
    val key = handler.handle(id, 1, emptyList()).getOrThrow().leftOrNull().shouldNotBeNull()
    handler.updateCachedResponse(key, id, Result.failure(RuntimeException("boom"))).getOrThrow()

    handler.handle(id, 1, emptyList()).shouldBeFailure<CachedError>()
    handler.clearCachedErrors(id).getOrThrow() shouldBe 1
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

  override fun deleteErrorResponsesForRequest(requestId: TestId): Result<Int> = result {
    val keys = responses.filter { (key, response) ->
      key.requestId == requestId && response.error != null
    }.keys
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
