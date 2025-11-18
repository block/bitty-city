package xyz.block.bittycity.common.idempotency

import xyz.block.bittycity.common.store.Operations
import xyz.block.domainapi.InfoOnly

/**
 * Operations interface for managing idempotent responses.
 *
 * @param ID The type of the request identifier (e.g., WithdrawalToken, DepositToken).
 * @param REQ The type of the requirement identifier used in Domain API (e.g., RequirementId).
 */
interface IdempotencyOperations<ID, REQ> : Operations {

  fun findResponse(
    idempotencyKey: String,
    requestId: ID,
  ): Result<IdempotentResponse<ID, REQ>?>

  fun insertResponse(response: IdempotentResponse<ID, REQ>): Result<IdempotentResponse<ID, REQ>>

  fun updateResponse(
    idempotencyKey: String,
    response: IdempotentResponse<ID, REQ>
  ): Result<IdempotentResponse<ID, REQ>>
}

sealed class IdempotencyStoreError(message: String) :
  Exception(message),
  InfoOnly

class ResponseVersionMismatch(val version: Long, val requestId: String) :
  IdempotencyStoreError("Response not at expected version $version: $requestId")

data class ResponseNotPresent(val idempotencyKey: String) :
  IdempotencyStoreError("Response not present $idempotencyKey")

data class AlreadyProcessingException(override val cause: Throwable?) :
  IdempotencyStoreError("Response is already being processed")
