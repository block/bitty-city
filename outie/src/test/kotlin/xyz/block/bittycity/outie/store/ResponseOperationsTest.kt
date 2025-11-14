package xyz.block.bittycity.outie.store

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.outie.jooq.JooqResponseOperations
import xyz.block.bittycity.outie.jooq.ResponseNotPresent
import xyz.block.bittycity.outie.jooq.generated.tables.references.WITHDRAWAL_RESPONSES
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Response
import xyz.block.bittycity.outie.models.SerializableError
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import xyz.block.domainapi.ExecuteResponse

class ResponseOperationsTest : BittyCityTestCase() {

  @Inject
  private lateinit var transactor: Transactor<ResponseOperations>

  @Test
  fun `find returns null when no response exists`() = runTest {
    val idempotencyKey = "non-existent-key"
    val withdrawalToken = Arbitrary.withdrawalToken.next()

    val result = transactor.transactReadOnly("test") {
      findResponse(idempotencyKey, withdrawalToken)
    }

    result.getOrThrow() shouldBe null
  }

  @Test
  fun `find returns response when it exists`() = runTest {
    val idempotencyKey = "test-key-123"
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val executeResponse = ExecuteResponse<WithdrawalToken, RequirementId>(
      id = withdrawalToken,
      interactions = emptyList(),
      nextEndpoint = null
    )
    val response = Response(
      idempotencyKey = idempotencyKey,
      withdrawalToken = withdrawalToken,
      version = 1L,
      result = executeResponse
    )

    // Insert the response first
    transactor.transact("insert") { insertResponse(response) }.getOrThrow()

    // Now find it
    val result = transactor.transactReadOnly("find") {
      findResponse(idempotencyKey, withdrawalToken)
    }

    result.getOrThrow().shouldNotBeNull() should {
      it.idempotencyKey shouldBe idempotencyKey
      it.withdrawalToken shouldBe withdrawalToken
      it.version shouldBe 1L
      it.result shouldBe executeResponse
      it.error shouldBe null
    }
  }

  @Test
  fun `find returns response with error when it exists`() = runTest { app ->
    val idempotencyKey = "test-key-error"
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val error = SerializableError("Test error", "java.lang.RuntimeException")
    val response = Response(
      idempotencyKey = idempotencyKey,
      withdrawalToken = withdrawalToken,
      version = 1L,
      error = error
    )

    // Insert the response first
    transactor.transact("insert") { insertResponse(response) }.getOrThrow()

    // Now find it
    val result = transactor.transactReadOnly("find") {
      findResponse(idempotencyKey, withdrawalToken)
    }

    result.getOrThrow().shouldNotBeNull() should {
      it.idempotencyKey shouldBe idempotencyKey
      it.withdrawalToken shouldBe withdrawalToken
      it.version shouldBe 1L
      it.result shouldBe null
      it.error shouldBe error
    }
  }

  @Test
  fun `insert creates new response successfully`() = runTest { app ->
    val idempotencyKey = "test-insert-key"
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val executeResponse = ExecuteResponse<WithdrawalToken, RequirementId>(
      id = withdrawalToken,
      interactions = emptyList(),
      nextEndpoint = null
    )
    val response = Response(
      idempotencyKey = idempotencyKey,
      withdrawalToken = withdrawalToken,
      version = 0L,
      result = executeResponse
    )

    val insertedResponse = transactor.transact("insert") { insertResponse(response) }.getOrThrow()

    insertedResponse.idempotencyKey shouldBe idempotencyKey
    insertedResponse.withdrawalToken shouldBe withdrawalToken
    insertedResponse.version shouldBe 1L
    insertedResponse.result shouldBe executeResponse
    insertedResponse.error shouldBe null

    // Verify it was actually inserted
    val record = dslContext.select(JooqResponseOperations.responseFields)
      .from(WITHDRAWAL_RESPONSES)
      .where(WITHDRAWAL_RESPONSES.IDEMPOTENCY_KEY.eq(idempotencyKey))
      .and(WITHDRAWAL_RESPONSES.WITHDRAWAL_TOKEN.eq(withdrawalToken.toString()))
      .fetchOne().shouldNotBeNull()
    record.get(WITHDRAWAL_RESPONSES.IDEMPOTENCY_KEY) shouldBe idempotencyKey
    record.get(WITHDRAWAL_RESPONSES.WITHDRAWAL_TOKEN) shouldBe withdrawalToken.toString()
    record.get(WITHDRAWAL_RESPONSES.VERSION)?.toLong() shouldBe 1L
  }

  @Test
  fun `insert creates new response with error successfully`() = runTest { app ->
    val idempotencyKey = "test-insert-error-key"
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val error = SerializableError("Test error", "java.lang.IllegalArgumentException")
    val response = Response(
      idempotencyKey = idempotencyKey,
      withdrawalToken = withdrawalToken,
      version = 0L,
      error = error
    )

    val insertedResponse = transactor.transact("insert") { insertResponse(response) }.getOrThrow()

    insertedResponse.idempotencyKey shouldBe idempotencyKey
    insertedResponse.withdrawalToken shouldBe withdrawalToken
    insertedResponse.version shouldBe 1L
    insertedResponse.result shouldBe null
    insertedResponse.error shouldBe error
  }

  @Test
  fun `update updates existing response successfully`() = runTest { app ->
    val idempotencyKey = "test-update-key"
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val originalResponse = ExecuteResponse<WithdrawalToken, RequirementId>(
      id = withdrawalToken,
      interactions = emptyList(),
      nextEndpoint = null
    )
    val updatedResponse = ExecuteResponse<WithdrawalToken, RequirementId>(
      id = withdrawalToken,
      interactions = listOf(),
      nextEndpoint = null
    )

    // Insert original response
    val original = Response(
      idempotencyKey = idempotencyKey,
      withdrawalToken = withdrawalToken,
      version = 0L,
      result = originalResponse
    )

    transactor.transact("insert") { insertResponse(original) }.getOrThrow()

    // Update the response
    val updated = Response(
      idempotencyKey = idempotencyKey,
      withdrawalToken = withdrawalToken,
      version = 1L,
      result = updatedResponse
    )

    val updatedResponseResult =
      transactor.transact("insert") { updateResponse(idempotencyKey, updated) }.getOrThrow()
    updatedResponseResult.idempotencyKey shouldBe idempotencyKey
    updatedResponseResult.withdrawalToken shouldBe withdrawalToken
    updatedResponseResult.version shouldBe 2L
    updatedResponseResult.result shouldBe updatedResponse
    updatedResponseResult.error shouldBe null
  }

  @Test
  fun `update updates existing response with error successfully`() = runTest { app ->
    val idempotencyKey = "test-update-error-key"
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val originalResponse = ExecuteResponse<WithdrawalToken, RequirementId>(
      id = withdrawalToken,
      interactions = emptyList(),
      nextEndpoint = null
    )
    val error = SerializableError("Updated error", "java.lang.IllegalStateException")

    // Insert original response
    val original = Response(
      idempotencyKey = idempotencyKey,
      withdrawalToken = withdrawalToken,
      version = 0L,
      result = originalResponse
    )

    transactor.transact("insert") { insertResponse(original) }.getOrThrow()

    // Update the response with error
    val updated = Response(
      idempotencyKey = idempotencyKey,
      withdrawalToken = withdrawalToken,
      version = 1L,
      error = error
    )

    transactor.transact("update") {
      updateResponse(idempotencyKey, updated)
    }.getOrThrow() should { updatedResponseResult ->
      updatedResponseResult.idempotencyKey shouldBe idempotencyKey
      updatedResponseResult.withdrawalToken shouldBe withdrawalToken
      updatedResponseResult.version shouldBe 2L
      updatedResponseResult.result shouldBe null
      updatedResponseResult.error shouldBe error
    }
  }

  @Test
  fun `update fails when response does not exist`() = runTest { app ->
    val idempotencyKey = "non-existent-update-key"
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val response = Response(
      idempotencyKey = idempotencyKey,
      withdrawalToken = withdrawalToken,
      version = 1L,
      result = ExecuteResponse(
        id = withdrawalToken,
        interactions = emptyList(),
        nextEndpoint = null
      )
    )

    val result = transactor.transact("update") { updateResponse(idempotencyKey, response) }

    result.shouldBeFailure()
      .cause?.message shouldBe "Response not present $idempotencyKey"
  }

  @Test
  fun `update does not fail with version mismatch when response version is different`() = runTest {
    val idempotencyKey = "test-version-mismatch-key"
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val originalResponse = ExecuteResponse<WithdrawalToken, RequirementId>(
      id = withdrawalToken,
      interactions = emptyList(),
      nextEndpoint = null
    )

    // Insert original response
    val original = Response(
      idempotencyKey = idempotencyKey,
      withdrawalToken = withdrawalToken,
      version = 0L,
      result = originalResponse
    )

    transactor.transact("insert") { insertResponse(original) }.getOrThrow()

    // Try to update with wrong version
    val updated = Response(
      idempotencyKey = idempotencyKey,
      withdrawalToken = withdrawalToken,
      version = 5L, // Wrong version
      result = originalResponse
    )

    transactor.transact("update") { updateResponse(idempotencyKey, updated) }.getOrThrow()
      .version shouldBe 6L
  }

  @Test
  fun `find returns null when idempotency key matches but withdrawal token differs`() = runTest { app ->
    val idempotencyKey = "test-key-token-mismatch"
    val withdrawalToken1 = Arbitrary.withdrawalToken.next()
    val withdrawalToken2 = Arbitrary.withdrawalToken.next()
    val response = Response(
      idempotencyKey = idempotencyKey,
      withdrawalToken = withdrawalToken1,
      version = 0L,
      result = ExecuteResponse<WithdrawalToken, RequirementId>(
        id = withdrawalToken1,
        interactions = emptyList(),
        nextEndpoint = null
      )
    )

    // Insert response with withdrawalToken1
    transactor.transact("insert") { insertResponse(response) }.getOrThrow()

    // Try to find with withdrawalToken2
    val result = transactor.transactReadOnly("find") {
      findResponse(idempotencyKey, withdrawalToken2)
    }

    result.getOrThrow() shouldBe null
  }

  @Test
  fun `insert handles complex ExecuteResponse with interactions`() = runTest { app ->
    val idempotencyKey = "test-complex-response-key"
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val executeResponse = ExecuteResponse<WithdrawalToken, RequirementId>(
      id = withdrawalToken,
      interactions = emptyList(),
      nextEndpoint = null
    )
    val response = Response(
      idempotencyKey = idempotencyKey,
      withdrawalToken = withdrawalToken,
      version = 0L,
      result = executeResponse
    )

    transactor.transact("insert") {
      insertResponse(response)
    }.getOrThrow() should { insertedResponse ->
      insertedResponse.idempotencyKey shouldBe idempotencyKey
      insertedResponse.withdrawalToken shouldBe withdrawalToken
      insertedResponse.version shouldBe 1L
      insertedResponse.result shouldBe executeResponse
      insertedResponse.error shouldBe null
    }

    // Verify we can find it back
    val foundResult = transactor.transactReadOnly("find") {
      findResponse(idempotencyKey, withdrawalToken)
    }

    foundResult.getOrThrow().shouldNotBeNull() should {
      it.result shouldBe executeResponse
    }
  }

  @Test
  fun `insert handles null result and error`() = runTest { app ->
    val idempotencyKey = "test-null-response-key"
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val response = Response(
      idempotencyKey = idempotencyKey,
      withdrawalToken = withdrawalToken,
      version = 0L,
      result = null,
      error = null
    )

    transactor.transact("insert") {
      insertResponse(response)
    }.getOrThrow() should { insertedResponse ->
      insertedResponse.idempotencyKey shouldBe idempotencyKey
      insertedResponse.withdrawalToken shouldBe withdrawalToken
      insertedResponse.version shouldBe 1L
      insertedResponse.result shouldBe null
      insertedResponse.error shouldBe null
    }
  }

  @Test
  fun `update handles multiple consecutive updates`() = runTest { app ->
    val idempotencyKey = "test-multiple-updates-key"
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val originalResponse = ExecuteResponse<WithdrawalToken, RequirementId>(
      id = withdrawalToken,
      interactions = emptyList(),
      nextEndpoint = null
    )

    // Insert original response
    val original = Response(
      idempotencyKey = idempotencyKey,
      withdrawalToken = withdrawalToken,
      version = 0L,
      result = originalResponse
    )

    transactor.transact("insert") { insertResponse(original) }.getOrThrow()

    // First update
    val firstUpdate = Response(
      idempotencyKey = idempotencyKey,
      withdrawalToken = withdrawalToken,
      version = 1L,
      result = originalResponse
    )

    transactor.transact("update1") {
      updateResponse(idempotencyKey, firstUpdate)
    }.getOrThrow().version shouldBe 2L

    // Second update
    val secondUpdate = Response(
      idempotencyKey = idempotencyKey,
      withdrawalToken = withdrawalToken,
      version = 2L,
      result = originalResponse
    )

    transactor.transact("update2") {
      updateResponse(idempotencyKey, secondUpdate)
    }.getOrThrow().version shouldBe 3L
  }

  @Test
  fun `find handles malformed JSON gracefully`() = runTest { app ->
    val idempotencyKey = "test-malformed-json-key"
    val withdrawalToken = Arbitrary.withdrawalToken.next()

    // Insert response with valid JSON that doesn't match ExecuteResponse structure
    dslContext.insertInto(WITHDRAWAL_RESPONSES)
      .set(WITHDRAWAL_RESPONSES.IDEMPOTENCY_KEY, idempotencyKey)
      .set(WITHDRAWAL_RESPONSES.WITHDRAWAL_TOKEN, withdrawalToken.toString())
      .set(WITHDRAWAL_RESPONSES.VERSION, org.jooq.types.ULong.valueOf(1L))
      .set(WITHDRAWAL_RESPONSES.RESPONSE_SNAPSHOT,
        org.jooq.JSON.valueOf("""{"invalid": "structure"}"""))
      .set(WITHDRAWAL_RESPONSES.ERROR_SNAPSHOT, null as org.jooq.JSON?)
      .execute()

    // Try to find it - should fail gracefully
    val result = transactor.transactReadOnly("find") {
      findResponse(idempotencyKey, withdrawalToken)
    }

    result.shouldBeFailure().message shouldContain "Failed to deserialize response"
  }

  @Test
  fun `find handles malformed error JSON gracefully`() = runTest { app ->
    val idempotencyKey = "test-malformed-error-json-key"
    val withdrawalToken = Arbitrary.withdrawalToken.next()

    // Insert response with valid JSON that doesn't match SerializableError structure
    dslContext.insertInto(WITHDRAWAL_RESPONSES)
      .set(WITHDRAWAL_RESPONSES.IDEMPOTENCY_KEY, idempotencyKey)
      .set(WITHDRAWAL_RESPONSES.WITHDRAWAL_TOKEN, withdrawalToken.toString())
      .set(WITHDRAWAL_RESPONSES.VERSION, org.jooq.types.ULong.valueOf(1L))
      .set(WITHDRAWAL_RESPONSES.RESPONSE_SNAPSHOT, null as org.jooq.JSON?)
      .set(WITHDRAWAL_RESPONSES.ERROR_SNAPSHOT,
        org.jooq.JSON.valueOf("""{"invalid": "error structure"}"""))
      .execute()

    // Try to find it - should fail gracefully
    val result = transactor.transactReadOnly("find") {
      findResponse(idempotencyKey, withdrawalToken)
    }

    result.shouldBeFailure() should { error ->
      error.message shouldContain "Failed to deserialize error details"
    }
  }
}
