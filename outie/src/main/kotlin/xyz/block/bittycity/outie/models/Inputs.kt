package xyz.block.bittycity.outie.models

import xyz.block.domainapi.Input

data class Inputs(
  val id: WithdrawalToken,
  val backCounter: Int,
  val hurdleResponses: List<Input.HurdleResponse<RequirementId>>
)
