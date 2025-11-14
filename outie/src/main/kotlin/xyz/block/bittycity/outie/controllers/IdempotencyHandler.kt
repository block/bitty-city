package xyz.block.bittycity.outie.controllers

import app.cash.quiver.extensions.success
import arrow.core.Either
import arrow.core.left
import arrow.core.raise.result
import arrow.core.right
import xyz.block.bittycity.outie.models.Inputs
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Response
import xyz.block.bittycity.outie.models.SerializableError
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.ResponseOperations
import xyz.block.bittycity.common.store.Transactor
import com.squareup.moshi.Moshi
import jakarta.inject.Inject
import xyz.block.bittycity.outie.store.AlreadyProcessingException
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.ExecuteResponse
import xyz.block.domainapi.Input
import java.util.zip.CRC32

class IdempotencyHandler @Inject constructor(
  private val moshi: Moshi,
  private val transactor: Transactor<ResponseOperations>,
) {
  fun handle(
    id: WithdrawalToken,
    backCounter: Int,
    hurdleResponses: List<Input.HurdleResponse<RequirementId>>
  ): Result<Either<String, ExecuteResponse<WithdrawalToken, RequirementId>>> = result {
    val hash = hashExecuteParameters(id, backCounter, hurdleResponses)
    transactor.transact("Handle idempotency") {
      val cachedResponse = findResponse(hash, id).bind()
      if (cachedResponse == null) {
        insertResponse(Response(hash, id, 0))
          .recover { error ->
            when (error) {
              // This means another call inserted the response first so we bail and return an already processing error
              is AlreadyProcessingException -> raise(DomainApiError.AlreadyProcessing(id.toString()))
              else -> raise(error)
            }
          }
        hash.left()
      } else {
        processCachedResponse(cachedResponse, id).bind().right()
      }.success()
    }.bind()
  }

  fun updateCachedResponse(
    idempotencyKey: String,
    id: WithdrawalToken,
    response: Result<ExecuteResponse<WithdrawalToken, RequirementId>>
  ): Result<Response> = result {
    transactor.transact("Update response") {
      val currentResponse: Response = findResponse(idempotencyKey, id).bind() ?: raise(
        IllegalStateException("No response found for idempotencyKey: $idempotencyKey")
      )
      updateResponse(
        idempotencyKey,
        buildResponse(id, idempotencyKey, currentResponse.version, response)
      )
    }.bind()
  }

  private fun buildResponse(
    id: WithdrawalToken,
    idempotencyKey: String,
    version: Long,
    response: Result<ExecuteResponse<WithdrawalToken, RequirementId>>
  ): Response =
    response.fold(
      { Response(idempotencyKey, id, version, it) },
      { Response(idempotencyKey, id, version, error = SerializableError.from(it)) }
    )

  /**
   * Computes a hash value based on the id of the withdrawal and the hurdle responses. This identifies an
   * identical call to the execute endpoint. The MurmurHash3 128-bit algorithm is used to compute the hash
   * value. This is a fast algorithm with very good distribution.
   *
   * @param id The id of the withdrawal.
   * @param hurdleResponses The responses to the hurdles sent by the client.
   */
  private fun hashExecuteParameters(
    id: WithdrawalToken,
    backCounter: Int,
    hurdleResponses: List<Input.HurdleResponse<RequirementId>>
  ): String {
    val json = serialiseInputs(Inputs(id, backCounter, hurdleResponses))
    val data = json.toByteArray(Charsets.UTF_8)
    val crc32 = CRC32()
    crc32.update(data)
    val hash = crc32.value

    // Convert 64-bit hash to a single 16-character hex string
    return "%016x".format(hash)
  }

  fun serialiseInputs(inputs: Inputs): String {
    return moshi.adapter(Inputs::class.java).toJson(inputs)
  }

  /**
   * Looks at a cached result and returns it if complete or raises an AlreadyProcessing error if it is not.
   *
   * @param response The cached result.
   * @param id The id of the pizza order.
   * @return The cached result if it is complete, or an AlreadyProcessing error if it is not.
   */
  private fun processCachedResponse(
    response: Response,
    id: WithdrawalToken
  ): Result<ExecuteResponse<WithdrawalToken, RequirementId>> = result {
    when {
      response.result != null -> response.result!!
      response.error != null -> raise((response.error as SerializableError).asCachedError())
      else -> raise(DomainApiError.AlreadyProcessing(id.toString()))
    }
  }
}
