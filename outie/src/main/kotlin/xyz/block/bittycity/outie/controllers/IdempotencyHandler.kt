package xyz.block.bittycity.outie.controllers

import xyz.block.bittycity.common.idempotency.IdempotencyHandler as GenericIdempotencyHandler
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.WithdrawalToken

/**
 * Withdrawal-specific idempotency handler.
 * This is a type alias for the generic IdempotencyHandler.
 */
typealias IdempotencyHandler = GenericIdempotencyHandler<WithdrawalToken, RequirementId>
