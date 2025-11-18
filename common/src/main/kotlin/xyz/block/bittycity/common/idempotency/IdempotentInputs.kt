package xyz.block.bittycity.common.idempotency

import xyz.block.domainapi.Input

/**
 * Represents the inputs to an idempotent Domain API execute call.
 *
 * @param ID The type of the request identifier (e.g., WithdrawalToken, DepositToken).
 * @param REQ The type of the requirement identifier used in Domain API (e.g., RequirementId).
 * @param id The identifier of the request.
 * @param backCounter The back counter for the request.
 * @param hurdleResponses The hurdle responses provided by the client.
 */
data class IdempotentInputs<ID, REQ>(
  val id: ID,
  val backCounter: Int,
  val hurdleResponses: List<Input.HurdleResponse<REQ>>
)
