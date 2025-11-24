package xyz.block.bittycity.innie.models

import java.time.Instant

data class DepositTransitionEvent(
  val id: Long,
  val createdAt: Instant,
  val updatedAt: Instant,
  val version: Long,
  val depositId: Long,
  val from: DepositState?,
  val to: DepositState,
  val isProcessed: Boolean,
  val depositSnapshot: String?,
)
