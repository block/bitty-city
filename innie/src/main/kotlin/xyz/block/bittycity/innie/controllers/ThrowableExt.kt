package xyz.block.bittycity.innie.controllers

import xyz.block.bittycity.common.client.IneligibleCustomer
import xyz.block.bittycity.common.client.RiskBlocked
import xyz.block.bittycity.innie.models.DepositFailureReason

fun Throwable.toFailureReason(): DepositFailureReason = when (this) {
  is IneligibleCustomer -> DepositFailureReason.INELIGIBLE
  is RiskBlocked -> DepositFailureReason.RISK_BLOCKED
  else -> DepositFailureReason.UNKNOWN
}
