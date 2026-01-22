package xyz.block.bittycity.outie.testing.fakes

import arrow.core.raise.result
import xyz.block.bittycity.common.idempotency.AlreadyProcessingException
import xyz.block.bittycity.common.idempotency.ResponseNotPresent
import xyz.block.bittycity.common.idempotency.ResponseVersionMismatch
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Response
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.ResponseOperations

class FakeResponseOperations : ResponseOperations {

  private data class CompositeKey(
    val idempotencyKey: String,
    val requestId: WithdrawalToken
  )

  private val responses = mutableMapOf<CompositeKey, Response>()

  override fun findResponse(
    idempotencyKey: String,
    requestId: WithdrawalToken
  ): Result<Response?> = result {
    responses[CompositeKey(idempotencyKey, requestId)]
  }

  override fun insertResponse(response: Response): Result<Response> = result {
    val key = CompositeKey(response.idempotencyKey, response.requestId)
    if (responses.containsKey(key)) {
      raise(AlreadyProcessingException(null))
    }
    val insertedResponse = response.copy(version = 1L)
    responses[key] = insertedResponse
    insertedResponse
  }

  override fun updateResponse(
    idempotencyKey: String,
    response: Response
  ): Result<Response> = result {
    val key = CompositeKey(idempotencyKey, response.requestId)
    val existing = responses[key]
      ?: raise(ResponseNotPresent(idempotencyKey))
    if (existing.version != response.version) {
      raise(ResponseVersionMismatch(response.version, response.requestId.toString()))
    }
    val updatedResponse = response.copy(version = response.version + 1)
    responses[key] = updatedResponse
    updatedResponse
  }

  fun reset() {
    responses.clear()
  }
}
