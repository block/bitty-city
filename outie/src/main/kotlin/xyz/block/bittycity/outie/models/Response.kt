package xyz.block.bittycity.outie.models

import xyz.block.bittycity.common.idempotency.IdempotentResponse

typealias Response = IdempotentResponse<WithdrawalToken, RequirementId>
