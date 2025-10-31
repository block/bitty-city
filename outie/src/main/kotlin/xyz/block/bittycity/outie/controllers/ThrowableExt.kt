package xyz.block.bittycity.outie.controllers

import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.validation.InsufficientBalance

fun Throwable.toFailureReason(): FailureReason = when (this) {
  is InsufficientBalance -> FailureReason.INSUFFICIENT_FUNDS
  is LimitWouldBeExceeded -> FailureReason.LIMITED
  else -> FailureReason.UNKNOWN
}
