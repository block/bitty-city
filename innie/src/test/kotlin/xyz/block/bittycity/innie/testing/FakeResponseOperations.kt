package xyz.block.bittycity.innie.testing

import arrow.core.raise.result
import xyz.block.bittycity.common.idempotency.AlreadyProcessingException
import xyz.block.bittycity.common.idempotency.IdempotentResponse
import xyz.block.bittycity.common.idempotency.ResponseNotPresent
import xyz.block.bittycity.common.idempotency.ResponseVersionMismatch
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.store.ResponseOperations
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory fake implementation of ResponseOperations for testing.
 */
class FakeResponseOperations : ResponseOperations {
  private val responses = ConcurrentHashMap<Pair<String, DepositToken>, IdempotentResponse<DepositToken, RequirementId>>()

  override fun findResponse(
    idempotencyKey: String,
    requestId: DepositToken
  ): Result<IdempotentResponse<DepositToken, RequirementId>?> = result {
    responses[idempotencyKey to requestId]
  }

  override fun insertResponse(
    response: IdempotentResponse<DepositToken, RequirementId>
  ): Result<IdempotentResponse<DepositToken, RequirementId>> = result {
    val key = response.idempotencyKey to response.requestId
    val existing = responses.putIfAbsent(key, response.copy(version = 1))
    if (existing != null) {
      raise(AlreadyProcessingException(RuntimeException("Response already exists")))
    }
    response.copy(version = 1)
  }

  override fun updateResponse(
    idempotencyKey: String,
    response: IdempotentResponse<DepositToken, RequirementId>
  ): Result<IdempotentResponse<DepositToken, RequirementId>> = result {
    val key = idempotencyKey to response.requestId
    val existing = responses[key] ?: raise(ResponseNotPresent(idempotencyKey))

    // Optimistic locking check
    if (existing.version != response.version) {
      raise(ResponseVersionMismatch(response.version, response.requestId.toString()))
    }

    val updated = response.copy(version = response.version + 1)
    responses[key] = updated
    updated
  }

  fun clear() {
    responses.clear()
  }
}
