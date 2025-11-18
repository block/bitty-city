package xyz.block.bittycity.outie.jooq

import app.cash.quiver.extensions.catch
import app.cash.quiver.extensions.mapFailure
import arrow.core.raise.result
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types.newParameterizedType
import org.jooq.DSLContext
import org.jooq.JSON
import org.jooq.Record
import org.jooq.exception.DataAccessException
import org.jooq.types.ULong
import xyz.block.bittycity.common.idempotency.AlreadyProcessingException
import xyz.block.bittycity.common.idempotency.ResponseNotPresent
import xyz.block.bittycity.common.idempotency.ResponseVersionMismatch
import xyz.block.bittycity.outie.jooq.generated.tables.WithdrawalResponses.Companion.WITHDRAWAL_RESPONSES
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Response
import xyz.block.bittycity.outie.models.SerializableError
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.ResponseOperations
import xyz.block.domainapi.ExecuteResponse

class JooqResponseOperations(
    private val context: DSLContext,
    private val moshi: Moshi
) : ResponseOperations {

    private val executeResponseAdapter: JsonAdapter<ExecuteResponse<WithdrawalToken, RequirementId>?> =
        moshi.adapter<ExecuteResponse<WithdrawalToken, RequirementId>>(
            newParameterizedType(
                ExecuteResponse::class.java,
                WithdrawalToken::class.java,
                RequirementId::class.java
            ))
    private val serializableErrorAdapter = moshi.adapter(SerializableError::class.java)

    override fun findResponse(
        idempotencyKey: String,
        requestId: WithdrawalToken
    ): Result<Response?> = result {
        val record = context
            .select(responseFields)
            .from(WITHDRAWAL_RESPONSES)
            .where(WITHDRAWAL_RESPONSES.IDEMPOTENCY_KEY.eq(idempotencyKey))
            .and(WITHDRAWAL_RESPONSES.WITHDRAWAL_TOKEN.eq(requestId.toString()))
            .fetchOne()

        record?.let { toResponseModel(it).bind() }
    }

    override fun insertResponse(response: Response): Result<Response> = result {
        val responseJsonString = response.result?.let { executeResponseAdapter.toJson(it) }
        val errorJsonString = response.error?.let { serializableErrorAdapter.toJson(it) }
        val insertStep = context.insertInto(WITHDRAWAL_RESPONSES)
            .set(WITHDRAWAL_RESPONSES.IDEMPOTENCY_KEY, response.idempotencyKey)
            .set(WITHDRAWAL_RESPONSES.WITHDRAWAL_TOKEN, response.requestId.toString())
            .set(WITHDRAWAL_RESPONSES.VERSION, ULong.valueOf(1L))
            .set(
                WITHDRAWAL_RESPONSES.RESPONSE_SNAPSHOT,
                responseJsonString?.let {
                    JSON.valueOf(it)
                }
            )
            .set(WITHDRAWAL_RESPONSES.ERROR_SNAPSHOT, errorJsonString?.let { JSON.valueOf(it) })

        val insertedRecord = insertStep
            .returning(responseFields)
            .fetchOne()!!

        toResponseModel(insertedRecord).bind()
    }.mapFailure { e ->
        when (e) {
            is DataAccessException -> AlreadyProcessingException(e)
            else -> e
        }
    }

    override fun updateResponse(
        idempotencyKey: String,
        response: Response
    ): Result<Response> = result {
        val responseJsonString = response.result?.let {
            executeResponseAdapter.toJson(it)
        }
        val errorJsonString = response.error?.let {
            moshi.adapter(SerializableError::class.java).toJson(it)
        }
        val updateStep = context.update(WITHDRAWAL_RESPONSES)
            .set(WITHDRAWAL_RESPONSES.VERSION, ULong.valueOf(response.version + 1))
            .set(WITHDRAWAL_RESPONSES.IDEMPOTENCY_KEY, response.idempotencyKey)
            .set(WITHDRAWAL_RESPONSES.WITHDRAWAL_TOKEN, response.requestId.toString())
            .set(
                WITHDRAWAL_RESPONSES.RESPONSE_SNAPSHOT,
                responseJsonString?.let {
                    JSON.valueOf(it)
                }
            )
            .set(
                WITHDRAWAL_RESPONSES.ERROR_SNAPSHOT,
                errorJsonString?.let {
                    JSON.valueOf(it)
                }
            )
            .where(WITHDRAWAL_RESPONSES.IDEMPOTENCY_KEY.eq(idempotencyKey))
            .and(WITHDRAWAL_RESPONSES.WITHDRAWAL_TOKEN.eq(response.requestId.toString()))

        val updatedRows = updateStep.execute()
        val refreshed = findResponse(idempotencyKey, response.requestId).bind()
            ?: raise(ResponseNotPresent(idempotencyKey))
        when (updatedRows) {
            0 -> raise(ResponseVersionMismatch(response.version, response.requestId.toString()))
            else -> refreshed
        }
    }

    private fun toResponseModel(record: Record): Result<Response> = result {
        Response(
            idempotencyKey = record.get(WITHDRAWAL_RESPONSES.IDEMPOTENCY_KEY)!!,
            requestId = WithdrawalToken.parse(
                record.get(WITHDRAWAL_RESPONSES.WITHDRAWAL_TOKEN)!!
            ).bind(),
            version = record.get(WITHDRAWAL_RESPONSES.VERSION)!!.toLong(),
            result = record.get(WITHDRAWAL_RESPONSES.RESPONSE_SNAPSHOT)?.let {
                deserialiseResponse(it.toString()).bind()
            },
            error = record.get(WITHDRAWAL_RESPONSES.ERROR_SNAPSHOT)?.let {
                deserialiseError(it.toString()).bind()
            }
        )
    }

    private fun deserialiseResponse(
        jsonString: String
    ): Result<ExecuteResponse<WithdrawalToken, RequirementId>> = result {
        Result.catch {
            executeResponseAdapter.fromJson(jsonString)
                ?: raise(IllegalStateException("Failed to deserialize response"))
        }.mapFailure {
            IllegalStateException("Failed to deserialize response: ${it.message}", it)
        }.bind()
    }

    private fun deserialiseError(jsonString: String): Result<SerializableError> = result {
        Result.catch {
            serializableErrorAdapter.fromJson(jsonString)
                ?: raise(IllegalStateException("Failed to deserialize error details"))
        }.mapFailure {
            IllegalStateException("Failed to deserialize error details: ${it.message}", it)
        }.bind()
    }

    companion object {
        val responseFields = listOf(
            WITHDRAWAL_RESPONSES.ID,
            WITHDRAWAL_RESPONSES.VERSION,
            WITHDRAWAL_RESPONSES.CREATED_AT,
            WITHDRAWAL_RESPONSES.UPDATED_AT,
            WITHDRAWAL_RESPONSES.IDEMPOTENCY_KEY,
            WITHDRAWAL_RESPONSES.WITHDRAWAL_TOKEN,
            WITHDRAWAL_RESPONSES.RESPONSE_SNAPSHOT,
            WITHDRAWAL_RESPONSES.ERROR_SNAPSHOT
        )
    }
}
