package xyz.block.bittycity.innie.controllers

import xyz.block.bittycity.common.client.IneligibleCustomer
import xyz.block.bittycity.common.client.RiskBlocked
import xyz.block.bittycity.innie.models.DepositFailureReason
import xyz.block.bittycity.innie.models.DepositReversalFailureReason

fun Throwable.toFailureReason(): DepositFailureReason = when (this) {
  is IneligibleCustomer -> DepositFailureReason.INELIGIBLE
  is RiskBlocked -> DepositFailureReason.RISK_BLOCKED
  else -> DepositFailureReason.UNKNOWN
}

fun Throwable.toReversalFailureReason(): DepositReversalFailureReason = when (this) {
  else -> DepositReversalFailureReason.UNKNOWN
}
