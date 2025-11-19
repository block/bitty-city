package xyz.block.bittycity.innie.controllers

import xyz.block.bittycity.common.idempotency.IdempotencyHandler as GenericIdempotencyHandler
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId

/**
 * Deposit-specific idempotency handler.
 * This is a type alias for the generic IdempotencyHandler.
 */
typealias IdempotencyHandler = GenericIdempotencyHandler<DepositToken, RequirementId>
