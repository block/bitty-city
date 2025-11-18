package xyz.block.bittycity.outie.models

import xyz.block.bittycity.common.idempotency.IdempotentInputs

typealias Inputs = IdempotentInputs<WithdrawalToken, RequirementId>
