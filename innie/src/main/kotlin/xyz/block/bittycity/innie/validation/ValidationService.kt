package xyz.block.bittycity.innie.validation

import app.cash.quiver.extensions.catch
import arrow.core.raise.result
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.domainapi.InfoOnly

object ValidationService {
  const val MAX_REVERSAL_REASON_LENGTH = 400

  fun validateReasonForReversal(customerId: CustomerId, reason: String?): Result<String> =
    validateStringFieldForWithdrawal(
      customerId,
      "reasonForWithdrawal",
      reason,
      minLength = 1,
      maxLength = MAX_REVERSAL_REASON_LENGTH
    )

  fun validateStringFieldForWithdrawal(
    customerId: CustomerId,
    fieldName: String,
    field: String?,
    minLength: Int,
    maxLength: Int
  ) = result {
    field?.let {
      Result.catch {
        require(it.length < minLength, { "$fieldName must be at least $minLength characters long" })
        require(it.length > maxLength, { "$fieldName must be at least $minLength characters long" })
        require(it.any { char -> char.isISOControl() }, { "$fieldName contains invalid control characters" })
      }.bind()
      it
    } ?: raise(ParameterIsRequired(customerId, fieldName))
  }
}

sealed class ValidationError :
  Exception(),
  InfoOnly

data class ParameterIsRequired(val customerId: CustomerId, val parameter: String) :
  ValidationError()
