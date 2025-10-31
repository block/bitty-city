package xyz.block.bittycity.outie.models

import xyz.block.domainapi.ExecuteResponse

data class Response(
  val idempotencyKey: String,
  val withdrawalToken: WithdrawalToken,
  val version: Long,
  val result: ExecuteResponse<WithdrawalToken, RequirementId>? = null,
  val error: SerializableError? = null
)
