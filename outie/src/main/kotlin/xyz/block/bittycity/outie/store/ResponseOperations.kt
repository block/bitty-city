package xyz.block.bittycity.outie.store

import xyz.block.bittycity.common.idempotency.IdempotencyOperations
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.WithdrawalToken

typealias ResponseOperations = IdempotencyOperations<WithdrawalToken, RequirementId>
