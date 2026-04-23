package xyz.block.bittycity.outie.controllers

import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.validation.AmountBelowFreeTier
import xyz.block.bittycity.outie.validation.AmountTooLow
import xyz.block.bittycity.outie.validation.InsufficientBalance
import xyz.block.bittycity.outie.validation.InvalidBlockTarget
import xyz.block.bittycity.outie.validation.InvalidNoteError
import xyz.block.bittycity.outie.validation.InvalidSourceBalanceToken
import xyz.block.bittycity.outie.validation.ParameterIsRequired

fun Throwable.toFailureReason(): FailureReason = when (this) {
  is InsufficientBalance -> FailureReason.INSUFFICIENT_FUNDS
  is LimitWouldBeExceeded -> FailureReason.LIMITED
  is AmountBelowFreeTier -> FailureReason.AMOUNT_BELOW_FREE_TIER
  is AmountTooLow -> FailureReason.AMOUNT_TOO_LOW
  is ParameterIsRequired -> FailureReason.PARAMETER_IS_REQUIRED
  is InvalidNoteError -> FailureReason.INVALID_NOTE
  is InvalidSourceBalanceToken -> FailureReason.INVALID_SOURCE_BALANCE_TOKEN
  is InvalidBlockTarget -> FailureReason.INVALID_BLOCK_TARGET
  else -> FailureReason.UNKNOWN
}
