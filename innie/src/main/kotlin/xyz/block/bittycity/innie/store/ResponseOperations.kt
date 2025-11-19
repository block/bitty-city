package xyz.block.bittycity.innie.store

import xyz.block.bittycity.common.idempotency.IdempotencyOperations
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId

/**
 * Deposit-specific type alias for response operations.
 */
typealias ResponseOperations = IdempotencyOperations<DepositToken, RequirementId>
