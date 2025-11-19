package xyz.block.bittycity.common.idempotency

import app.cash.quiver.extensions.success
import arrow.core.Either
import arrow.core.left
import arrow.core.raise.result
import arrow.core.right
import xyz.block.bittycity.common.store.Transactor
import com.squareup.moshi.Moshi
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.ExecuteResponse
import xyz.block.domainapi.Input
import java.util.zip.CRC32

/**
 * Generic idempotency handler for Domain API execute and resume calls.
 *
 * This handler manages idempotent execution of Domain API requests by:
 * - Hashing request parameters to create an idempotency key
 * - Checking for cached responses
 * - Preventing concurrent processing of identical requests
 * - Returning cached results when available
 *
 * @param ID The type of the request identifier (e.g., WithdrawalToken, DepositToken).
 * @param REQ The type of the requirement identifier used in Domain API (e.g., RequirementId).
 * @param moshi The Moshi instance for JSON serialization.
 * @param transactor The transactor for database operations.
 * @param inputsAdapter A function to create a Moshi adapter for IdempotentInputs.
 * @param resumeInputsAdapter A function to create a Moshi adapter for IdempotentResumeInputs.
 */
class IdempotencyHandler<ID, REQ>(
  private val moshi: Moshi,
  private val transactor: Transactor<IdempotencyOperations<ID, REQ>>,
  private val inputsAdapter: (Moshi) -> com.squareup.moshi.JsonAdapter<IdempotentInputs<ID, REQ>>,
  private val resumeInputsAdapter: (Moshi) -> com.squareup.moshi.JsonAdapter<IdempotentResumeInputs<ID, REQ>>
) {
  /**
   * Handles an idempotent execute request.
   *
   * @param id The request identifier.
   * @param backCounter The back counter.
   * @param hurdleResponses The hurdle responses from the client.
   * @return Either a new idempotency key (Left) if this is a new request, or the cached response (Right).
   */
  fun handle(
    id: ID,
    backCounter: Int,
    hurdleResponses: List<Input.HurdleResponse<REQ>>
  ): Result<Either<String, ExecuteResponse<ID, REQ>>> = result {
    val hash = hashExecuteParameters(id, backCounter, hurdleResponses)
    transactor.transact("Handle idempotency") {
      val cachedResponse = findResponse(hash, id).bind()
      if (cachedResponse == null) {
        insertResponse(IdempotentResponse(hash, id, 0))
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

  /**
   * Handles an idempotent resume request.
   *
   * @param id The request identifier.
   * @param resumeResult The resume result from the client.
   * @return Either a new idempotency key (Left) if this is a new request, or the cached response (Right).
   */
  fun handleResume(
    id: ID,
    resumeResult: Input.ResumeResult<REQ>
  ): Result<Either<String, ExecuteResponse<ID, REQ>>> = result {
    val hash = hashResumeParameters(id, resumeResult)
    transactor.transact("Handle resume idempotency") {
      val cachedResponse = findResponse(hash, id).bind()
      if (cachedResponse == null) {
        insertResponse(IdempotentResponse(hash, id, 0))
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

  /**
   * Updates a cached response with the result of processing.
   *
   * @param idempotencyKey The idempotency key.
   * @param id The request identifier.
   * @param response The processing result.
   * @return The updated cached response.
   */
  fun updateCachedResponse(
    idempotencyKey: String,
    id: ID,
    response: Result<ExecuteResponse<ID, REQ>>
  ): Result<IdempotentResponse<ID, REQ>> = result {
    transactor.transact("Update response") {
      val currentResponse: IdempotentResponse<ID, REQ> = findResponse(idempotencyKey, id).bind() ?: raise(
        IllegalStateException("No response found for idempotencyKey: $idempotencyKey")
      )
      updateResponse(
        idempotencyKey,
        buildResponse(id, idempotencyKey, currentResponse.version, response)
      )
    }.bind()
  }

  private fun buildResponse(
    id: ID,
    idempotencyKey: String,
    version: Long,
    response: Result<ExecuteResponse<ID, REQ>>
  ): IdempotentResponse<ID, REQ> =
    response.fold(
      { IdempotentResponse(idempotencyKey, id, version, it) },
      { IdempotentResponse(idempotencyKey, id, version, error = SerializableError.from(it)) }
    )

  /**
   * Computes a hash value based on the id of the request and the hurdle responses. This identifies an
   * identical call to the execute endpoint. The CRC32 algorithm is used to compute the hash value.
   *
   * CRC32 produces a 32-bit hash which is acceptable here because:
   * - Hash collisions are scoped per request ID (which is a UUID)
   * - For a single request, the collision probability is negligible even with 100s of calls
   * - CRC32 is ~10x faster than cryptographic hashes like SHA-256
   *
   * @param id The id of the request.
   * @param backCounter The back counter.
   * @param hurdleResponses The responses to the hurdles sent by the client.
   */
  private fun hashExecuteParameters(
    id: ID,
    backCounter: Int,
    hurdleResponses: List<Input.HurdleResponse<REQ>>
  ): String {
    val json = serialiseInputs(IdempotentInputs(id, backCounter, hurdleResponses))
    val data = json.toByteArray(Charsets.UTF_8)
    val crc32 = CRC32()
    crc32.update(data)
    val hash = crc32.value

    // CRC32 produces a 32-bit hash, formatted as a 16-character hex string with leading zeros
    return "%016x".format(hash)
  }

  fun serialiseInputs(inputs: IdempotentInputs<ID, REQ>): String {
    return inputsAdapter(moshi).toJson(inputs)
  }

  /**
   * Computes a hash value based on the id of the request and the resume result. This identifies an
   * identical call to the resume endpoint. The CRC32 algorithm is used to compute the hash value.
   *
   * CRC32 produces a 32-bit hash which is acceptable here because:
   * - Hash collisions are scoped per request ID (which is a UUID)
   * - For a single request, the collision probability is negligible even with 100s of calls
   * - CRC32 is ~10x faster than cryptographic hashes like SHA-256
   *
   * @param id The id of the request.
   * @param resumeResult The resume result from the client.
   */
  private fun hashResumeParameters(
    id: ID,
    resumeResult: Input.ResumeResult<REQ>
  ): String {
    val json = serialiseResumeInputs(IdempotentResumeInputs(id, resumeResult))
    val data = json.toByteArray(Charsets.UTF_8)
    val crc32 = CRC32()
    crc32.update(data)
    val hash = crc32.value

    // CRC32 produces a 32-bit hash, formatted as a 16-character hex string with leading zeros
    return "%016x".format(hash)
  }

  fun serialiseResumeInputs(inputs: IdempotentResumeInputs<ID, REQ>): String =
    resumeInputsAdapter(moshi).toJson(inputs)

  /**
   * Looks at a cached result and returns it if complete or raises an AlreadyProcessing error if it is not.
   *
   * @param response The cached result.
   * @param id The id of the request.
   * @return The cached result if it is complete, or an AlreadyProcessing error if it is not.
   */
  private fun processCachedResponse(
    response: IdempotentResponse<ID, REQ>,
    id: ID
  ): Result<ExecuteResponse<ID, REQ>> = result {
    when {
      response.result != null -> response.result!!
      response.error != null -> raise((response.error as SerializableError).asCachedError())
      else -> raise(DomainApiError.AlreadyProcessing(id.toString()))
    }
  }
}
