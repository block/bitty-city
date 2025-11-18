package xyz.block.bittycity.outie.controllers

import app.cash.quiver.extensions.success
import xyz.block.bittycity.common.idempotency.CachedError
import xyz.block.bittycity.outie.models.Inputs
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.WithdrawalHurdleResponse
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.ExecuteResponse
import xyz.block.domainapi.Input
import xyz.block.domainapi.ResultCode
import java.util.UUID

class IdempotencyHandlerTest : BittyCityTestCase() {

  @Inject
  private lateinit var idempotencyHandler: IdempotencyHandler

  @Test
  fun `handle returns left with idempotency key when no cached response exists`() = runTest {
    // Consistent input will give consistent hash output
    val withdrawalToken = WithdrawalToken(UUID.fromString("6a0b96e9-0b04-4114-b005-272fac5e76ee"))
    val backCounter = 1
    val hurdleResponses = listOf<Input.HurdleResponse<RequirementId>>()

    idempotencyHandler.handle(withdrawalToken, backCounter, hurdleResponses).getOrThrow()
      .shouldBeLeft("0000000070b481e7")
  }

  @Test
  fun `handle returns cached response when one exists`() = runTest {
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val backCounter = 1
    val hurdleResponses = listOf<Input.HurdleResponse<RequirementId>>()
    val cachedResponse = ExecuteResponse<WithdrawalToken, RequirementId>(
      id = withdrawalToken,
      interactions = emptyList(),
      nextEndpoint = null
    )

    // First call creates the idempotency key
    val firstResult = idempotencyHandler.handle(withdrawalToken, backCounter, hurdleResponses)
    val idempotencyKey = firstResult.getOrThrow().shouldBeLeft()

    // Update the cached response with the actual result
    idempotencyHandler.updateCachedResponse(
      idempotencyKey = idempotencyKey,
      id = withdrawalToken,
      response = cachedResponse.success(),
    ).getOrThrow()

    // Second call should return the cached response
    idempotencyHandler.handle(withdrawalToken, backCounter, hurdleResponses)
      .getOrThrow().shouldBeRight(cachedResponse)
  }

  @Test
  fun `handle returns cached error when one exists`() = runTest {
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val backCounter = 1
    val hurdleResponses = listOf<Input.HurdleResponse<RequirementId>>()
    val error = RuntimeException("Test error")

    // First call creates the idempotency key
    val firstResult = idempotencyHandler.handle(withdrawalToken, backCounter, hurdleResponses)
    val idempotencyKey = firstResult.getOrThrow().shouldBeLeft()
    idempotencyHandler.updateCachedResponse(
      idempotencyKey,
      withdrawalToken,
      Result.failure(error)
    ).getOrThrow()

    // Second call should return the cached error
    val secondResult = idempotencyHandler.handle(withdrawalToken, backCounter, hurdleResponses)
    secondResult.shouldBeFailure(CachedError("[java.lang.RuntimeException] Test error"))
  }

  @Test
  fun `handle returns AlreadyProcessing when response exists but is incomplete`() = runTest {
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val backCounter = 1
    val hurdleResponses = listOf<Input.HurdleResponse<RequirementId>>()

    // First call creates the idempotency key but doesn't update it
    idempotencyHandler.handle(withdrawalToken, backCounter, hurdleResponses)
      .getOrThrow().shouldBeLeft()

    // Second call should return AlreadyProcessing since the response is incomplete
    idempotencyHandler.handle(withdrawalToken, backCounter, hurdleResponses)
      .shouldBeFailure(DomainApiError.AlreadyProcessing(withdrawalToken.toString()))
  }

  @Test
  fun `updateCachedResponse updates existing response with success result`() = runTest {
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val backCounter = 1
    val hurdleResponses = listOf<Input.HurdleResponse<RequirementId>>()
    val executeResponse = ExecuteResponse<WithdrawalToken, RequirementId>(
      id = withdrawalToken,
      interactions = emptyList(),
      nextEndpoint = null
    )

    // Create an idempotency key first
    val idempotencyKey = idempotencyHandler.handle(withdrawalToken, backCounter, hurdleResponses)
      .getOrThrow().shouldBeLeft()

    val result = idempotencyHandler.updateCachedResponse(
      idempotencyKey,
      withdrawalToken,
      Result.success(executeResponse)
    )

    result.getOrThrow() should { updatedResponse ->
      updatedResponse.idempotencyKey shouldBe idempotencyKey
      updatedResponse.requestId shouldBe withdrawalToken
      updatedResponse.version shouldBe 2L
      updatedResponse.result shouldBe executeResponse
      updatedResponse.error shouldBe null
    }
  }

  @Test
  fun `updateCachedResponse updates existing response with error result`() = runTest {
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val backCounter = 1
    val hurdleResponses = listOf<Input.HurdleResponse<RequirementId>>()
    val error = RuntimeException("Test error")

    // Create an idempotency key first
    val idempotencyKey = idempotencyHandler.handle(withdrawalToken, backCounter, hurdleResponses)
      .getOrThrow().shouldBeLeft()

    val result = idempotencyHandler.updateCachedResponse(
      idempotencyKey,
      withdrawalToken,
      Result.failure(error)
    )

    result.getOrThrow() should { updatedResponse ->
      updatedResponse.idempotencyKey shouldBe idempotencyKey
      updatedResponse.requestId shouldBe withdrawalToken
      updatedResponse.version shouldBe 2L
      updatedResponse.result shouldBe null
      updatedResponse.error.shouldNotBeNull() should {
        it.message shouldBe "Test error"
        it.type shouldBe "java.lang.RuntimeException"
      }
    }
  }

  @Test
  fun `updateCachedResponse fails when no response exists`() = runTest {
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val idempotencyKey = "non-existent-key"
    val executeResponse = ExecuteResponse<WithdrawalToken, RequirementId>(
      id = withdrawalToken,
      interactions = emptyList(),
      nextEndpoint = null
    )

    idempotencyHandler.updateCachedResponse(
      idempotencyKey,
      withdrawalToken,
      Result.success(executeResponse)
    ).shouldBeFailure()
  }

  @Test
  fun `serialiseInputs correctly serializes inputs to JSON`() = runTest {
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val backCounter = 42
    val hurdleResponses = listOf<Input.HurdleResponse<RequirementId>>()
    val inputs = Inputs(withdrawalToken, backCounter, hurdleResponses)

    idempotencyHandler.serialiseInputs(inputs) shouldBe
      """{"id":"$withdrawalToken","backCounter":42,"hurdleResponses":[]}"""
  }

  @Test
  fun `hashExecuteParameters generates consistent hash for same inputs`() = runTest {
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val backCounter = 1
    val hurdleResponses = listOf<Input.HurdleResponse<RequirementId>>()

    val result1 = idempotencyHandler.handle(withdrawalToken, backCounter, hurdleResponses)

    // First call should return Left (idempotency key)
    result1.getOrThrow().shouldBeLeft()

    // Second call should fail with AlreadyProcessing because the first call created a response
    // that's being processed
    idempotencyHandler.handle(withdrawalToken, backCounter, hurdleResponses)
      .shouldBeFailure(DomainApiError.AlreadyProcessing(withdrawalToken.toString()))
  }

  @Test
  fun `hashExecuteParameters generates different hash for different inputs`() = runTest {
    val withdrawalToken1 = Arbitrary.withdrawalToken.next()
    val withdrawalToken2 = Arbitrary.withdrawalToken.next()
    val backCounter = 1
    val hurdleResponses = listOf<Input.HurdleResponse<RequirementId>>()

    val result1 = idempotencyHandler.handle(withdrawalToken1, backCounter, hurdleResponses)
    val result2 = idempotencyHandler.handle(withdrawalToken2, backCounter, hurdleResponses)

    result1.getOrThrow() shouldNotBe result2.getOrThrow()
  }

  @Test
  fun `hashExecuteParameters generates different hash for different backCounter values`() = runTest {
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val hurdleResponses = listOf<Input.HurdleResponse<RequirementId>>()

    val result1 = idempotencyHandler.handle(withdrawalToken, 1, hurdleResponses)
    val result2 = idempotencyHandler.handle(withdrawalToken, 2, hurdleResponses)

    result1.getOrThrow() shouldNotBe result2.getOrThrow()
  }

  @Test
  fun `hashExecuteParameters generates different hash for different hurdle responses`() = runTest {
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val backCounter = 1
    val hurdleResponses1 = listOf<Input.HurdleResponse<RequirementId>>()
    val hurdleResponses2 = listOf(
      WithdrawalHurdleResponse.TargetWalletAddressHurdleResponse(
        code = ResultCode.CLEARED
      )
    )

    val result1 = idempotencyHandler.handle(withdrawalToken, backCounter, hurdleResponses1)
    val result2 = idempotencyHandler.handle(withdrawalToken, backCounter, hurdleResponses2)

    result1.getOrThrow() shouldNotBe result2.getOrThrow()
  }

  @Test
  fun `serialiseInputs handles complex hurdle responses correctly`() = runTest {
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val backCounter = 42
    val hurdleResponses = listOf(
      WithdrawalHurdleResponse.TargetWalletAddressHurdleResponse(
        code = ResultCode.CLEARED
      ),
      WithdrawalHurdleResponse.TargetWalletAddressHurdleResponse(
        code = ResultCode.FAILED
      )
    )
    val inputs = Inputs(withdrawalToken, backCounter, hurdleResponses)

    idempotencyHandler.serialiseInputs(inputs) should {
      it shouldContain withdrawalToken.toString()
      it shouldContain "backCounter"
      it shouldContain "hurdleResponses"
      it shouldContain "TARGET_WALLET_ADDRESS"
      it shouldContain "CLEARED"
      it shouldContain "FAILED"
    }
  }

  @Test
  fun `handle with different withdrawal tokens generates different idempotency keys`() = runTest {
    val withdrawalToken1 = Arbitrary.withdrawalToken.next()
    val withdrawalToken2 = Arbitrary.withdrawalToken.next()
    val backCounter = 1
    val hurdleResponses = listOf<Input.HurdleResponse<RequirementId>>()

    val result1 = idempotencyHandler.handle(withdrawalToken1, backCounter, hurdleResponses)
    val result2 = idempotencyHandler.handle(withdrawalToken2, backCounter, hurdleResponses)

    result1.getOrThrow() shouldNotBe result2.getOrThrow()
  }

  @Test
  fun `updateCachedResponse with different withdrawal token fails`() = runTest {
    val withdrawalToken1 = Arbitrary.withdrawalToken.next()
    val withdrawalToken2 = Arbitrary.withdrawalToken.next()
    val backCounter = 1
    val hurdleResponses = listOf<Input.HurdleResponse<RequirementId>>()
    val executeResponse = ExecuteResponse<WithdrawalToken, RequirementId>(
      id = withdrawalToken1,
      interactions = emptyList(),
      nextEndpoint = null
    )

    // Create an idempotency key with first token
    val idempotencyKey = idempotencyHandler.handle(withdrawalToken1, backCounter, hurdleResponses)
      .getOrThrow().shouldBeLeft()

    // Try to update with different withdrawal token
    val result = idempotencyHandler.updateCachedResponse(
      idempotencyKey,
      withdrawalToken2,
      Result.success(executeResponse)
    )

    result.shouldBeFailure()
  }

  @Test
  fun `handle returns AlreadyProcessing when another process is already handling the same request`() = runTest {
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val backCounter = 1
    val hurdleResponses = listOf<Input.HurdleResponse<RequirementId>>()

    // First call creates the idempotency key
    val firstResult = idempotencyHandler.handle(withdrawalToken, backCounter, hurdleResponses)
    firstResult.getOrThrow().shouldBeLeft()

    // Simulate another process trying to handle the same request
    // This should return AlreadyProcessing since the response exists but is incomplete
    idempotencyHandler.handle(withdrawalToken, backCounter, hurdleResponses).shouldBeFailure()
  }

  @Test
  fun `updateCachedResponse increments version correctly`() = runTest {
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val backCounter = 1
    val hurdleResponses = listOf<Input.HurdleResponse<RequirementId>>()
    val executeResponse = ExecuteResponse<WithdrawalToken, RequirementId>(
      id = withdrawalToken,
      interactions = emptyList(),
      nextEndpoint = null
    )

    // Create an idempotency key first
    val idempotencyKey = idempotencyHandler.handle(withdrawalToken, backCounter, hurdleResponses)
      .getOrThrow().shouldBeLeft()

    // First update
    val result1 = idempotencyHandler.updateCachedResponse(
      idempotencyKey,
      withdrawalToken,
      Result.success(executeResponse)
    )

    result1.getOrThrow().version shouldBe 2L

    // Second update
    val result2 = idempotencyHandler.updateCachedResponse(
      idempotencyKey,
      withdrawalToken,
      Result.success(executeResponse)
    )

    result2.getOrThrow().version shouldBe 3L
  }

  @Test
  fun `serialiseInputs handles empty hurdle responses`() = runTest {
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val backCounter = 0
    val hurdleResponses = emptyList<Input.HurdleResponse<RequirementId>>()
    val inputs = Inputs(withdrawalToken, backCounter, hurdleResponses)

    val json = idempotencyHandler.serialiseInputs(inputs)
    json shouldBe """{"id":"$withdrawalToken","backCounter":0,"hurdleResponses":[]}"""
  }

  @Test
  fun `updateCachedResponse with null result and error works correctly`() = runTest {
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val backCounter = 1
    val hurdleResponses = listOf<Input.HurdleResponse<RequirementId>>()
    val error = IllegalArgumentException("Invalid argument")

    // Create an idempotency key first
    val idempotencyKey = idempotencyHandler.handle(withdrawalToken, backCounter, hurdleResponses)
      .getOrThrow().shouldBeLeft()

    val result = idempotencyHandler.updateCachedResponse(
      idempotencyKey,
      withdrawalToken,
      Result.failure(error)
    )

    result.getOrThrow() should { updatedResponse ->
      updatedResponse.idempotencyKey shouldBe idempotencyKey
      updatedResponse.requestId shouldBe withdrawalToken
      updatedResponse.version shouldBe 2L
      updatedResponse.result shouldBe null
      updatedResponse.error.shouldNotBeNull() should {
        it.message shouldBe "Invalid argument"
        it.type shouldBe "java.lang.IllegalArgumentException"
      }
    }
  }

  @Test
  fun `serialiseInputs handles special characters in withdrawal token`() = runTest {
    val withdrawalToken = WithdrawalToken(UUID.fromString("12345678-1234-1234-1234-123456789012"))
    val backCounter = 1
    val hurdleResponses = listOf<Input.HurdleResponse<RequirementId>>()
    val inputs = Inputs(withdrawalToken, backCounter, hurdleResponses)

    val json = idempotencyHandler.serialiseInputs(inputs)

    json.shouldNotBeNull()
    json shouldContain "12345678-1234-1234-1234-123456789012"
    json shouldContain "backCounter"
    json shouldContain "hurdleResponses"
  }

  @Test
  fun `updateCachedResponse handles multiple consecutive updates`() = runTest {
    val withdrawalToken = Arbitrary.withdrawalToken.next()
    val backCounter = 1
    val hurdleResponses = listOf<Input.HurdleResponse<RequirementId>>()
    val executeResponse1 = ExecuteResponse<WithdrawalToken, RequirementId>(
      id = withdrawalToken,
      interactions = emptyList(),
      nextEndpoint = null
    )
    val executeResponse2 = ExecuteResponse<WithdrawalToken, RequirementId>(
      id = withdrawalToken,
      interactions = emptyList(),
      nextEndpoint = null
    )

    // Create an idempotency key first
    val idempotencyKey = idempotencyHandler.handle(withdrawalToken, backCounter, hurdleResponses)
      .getOrThrow().shouldBeLeft()

    // First update
    val result1 = idempotencyHandler.updateCachedResponse(
      idempotencyKey,
      withdrawalToken,
      Result.success(executeResponse1)
    )
    result1.getOrThrow() should { updatedResponse ->
      updatedResponse.version shouldBe 2L
      updatedResponse.result shouldBe executeResponse1
    }

    // Second update
    val result2 = idempotencyHandler.updateCachedResponse(
      idempotencyKey,
      withdrawalToken,
      Result.success(executeResponse2)
    )
    result2.getOrThrow() should { updatedResponse ->
      updatedResponse.version shouldBe 3L
      updatedResponse.result shouldBe executeResponse2
    }
  }
}
