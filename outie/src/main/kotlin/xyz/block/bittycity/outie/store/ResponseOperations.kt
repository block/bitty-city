package xyz.block.bittycity.outie.store

import xyz.block.bittycity.outie.models.Response
import xyz.block.bittycity.outie.models.WithdrawalToken

interface ResponseOperations : Operations {

  fun findResponse(
    idempotencyKey: String,
    withdrawalToken: WithdrawalToken,
  ): Result<Response?>

  fun insertResponse(response: Response): Result<Response>

  fun updateResponse(idempotencyKey: String, response: Response): Result<Response>

}

data class AlreadyProcessingException(override val cause: Throwable?) : Exception()
