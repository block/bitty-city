package xyz.block.bittycity.common.idempotency

import xyz.block.domainapi.ExecuteResponse

/**
 * Represents a cached response for an idempotent request.
 *
 * @param ID The type of the request identifier (e.g., WithdrawalToken, DepositToken).
 * @param REQ The type of the requirement identifier used in Domain API (e.g., RequirementId).
 * @param idempotencyKey The hash key identifying this specific request.
 * @param requestId The identifier of the request being processed.
 * @param version The optimistic locking version for updates.
 * @param result The successful result, if processing completed successfully.
 * @param error The error, if processing completed with an error.
 */
data class IdempotentResponse<ID, REQ>(
  val idempotencyKey: String,
  val requestId: ID,
  val version: Long,
  val result: ExecuteResponse<ID, REQ>? = null,
  val error: SerializableError? = null
)
