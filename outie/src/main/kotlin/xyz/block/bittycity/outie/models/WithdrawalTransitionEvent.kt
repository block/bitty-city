package xyz.block.bittycity.outie.models

import java.time.Instant

data class WithdrawalTransitionEvent(
  val id: Long,
  val createdAt: Instant,
  val updatedAt: Instant,
  val version: Long,
  val withdrawalId: Long,
  val from: WithdrawalState?,
  val to: WithdrawalState,
  val isProcessed: Boolean,
  val withdrawalSnapshot: String?,
)
