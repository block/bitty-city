package xyz.block.bittycity.common.idempotency

import xyz.block.domainapi.Input

/**
 * Inputs for a resume operation that need to be hashed for idempotency.
 *
 * @param ID The type of the request identifier (e.g., WithdrawalToken, DepositToken).
 * @param REQ The type of the requirement identifier used in Domain API (e.g., RequirementId).
 * @param id The request identifier.
 * @param resumeResult The resume result from the client.
 */
data class IdempotentResumeInputs<ID, REQ>(
  val id: ID,
  val resumeResult: Input.ResumeResult<REQ>
)
