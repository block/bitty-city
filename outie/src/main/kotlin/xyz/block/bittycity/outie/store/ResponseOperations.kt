package xyz.block.bittycity.outie.store

import xyz.block.bittycity.common.store.Operations
import xyz.block.bittycity.outie.models.Response
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.domainapi.InfoOnly

interface ResponseOperations : Operations {

  fun findResponse(
    idempotencyKey: String,
    withdrawalToken: WithdrawalToken,
  ): Result<Response?>

  fun insertResponse(response: Response): Result<Response>

  fun updateResponse(idempotencyKey: String, response: Response): Result<Response>

}

sealed class ResponseStoreError(message: String) :
  Exception(message),
  InfoOnly

data class ResponseVersionMismatch(val withdrawalResponse: Response) :
  ResponseStoreError("Response not at expected version ${withdrawalResponse.version}" +
          ": ${withdrawalResponse.withdrawalToken}"
  )

data class ResponseNotPresent(val idempotencyKey: String) :
  ResponseStoreError("Response not present $idempotencyKey")

data class AlreadyProcessingException(override val cause: Throwable?) :
  ResponseStoreError("Response is already being processed")
