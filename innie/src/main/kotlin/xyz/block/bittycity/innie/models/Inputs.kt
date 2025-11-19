package xyz.block.bittycity.innie.models

import xyz.block.bittycity.common.idempotency.IdempotentInputs

/**
 * Deposit-specific type alias for idempotent inputs.
 */
typealias Inputs = IdempotentInputs<DepositToken, RequirementId>
