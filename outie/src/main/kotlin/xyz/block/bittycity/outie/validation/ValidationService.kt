package xyz.block.bittycity.outie.validation

import arrow.core.raise.result
import xyz.block.bittycity.outie.client.LedgerClient
import xyz.block.bittycity.outie.models.BalanceId
import xyz.block.bittycity.outie.models.CustomerId
import xyz.block.bittycity.outie.models.Bitcoins
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import xyz.block.domainapi.InfoOnly

@Singleton
class ValidationService @Inject constructor(
  private val ledgerClient: LedgerClient,
  @Named("withdrawal.amounts.minimum") val minAmount: Long
) {
  fun validateAmount(
    customerId: CustomerId,
    balanceId: BalanceId,
    userAmount: Bitcoins?
  ): Result<Bitcoins> = result {
    userAmount?.let { amount ->
      val currentBalance = ledgerClient.getBalance(customerId, balanceId).bind()
      when {
        amount.units < minAmount -> {
          raise(AmountTooLow(customerId, userAmount, Bitcoins(minAmount)))
        }

        amount.units > currentBalance.units -> {
          raise(InsufficientBalance(currentBalance, Bitcoins(minAmount), currentBalance, amount))
        }

        else -> amount
      }
    } ?: raise(ParameterIsRequired(customerId, "amount"))
  }

  fun validateNote(customerId: CustomerId, note: String?): Result<String> =
    validateStringFieldForWithdrawal(
      customerId,
      "note",
      note,
      minLength = 0,
      maxLength = MAX_NOTE_LENGTH
    )

  fun validateReasonForWithdrawal(customerId: CustomerId, reason: String?): Result<String> =
    validateStringFieldForWithdrawal(
      customerId,
      "reasonForWithdrawal",
      reason,
      minLength = 1,
      maxLength = MAX_WITHDRAWAL_REASON_LENGTH
    )

  fun validateAttestationDestinationForWithdrawal(
    customerId: CustomerId,
    reason: String?
  ): Result<String> = validateStringFieldForWithdrawal(
    customerId,
    "selfAttestationDestination",
    reason,
    minLength = 1,
    maxLength = MAX_ATTESTATION_DESTINATION_LENGTH
  )

  fun validateStringFieldForWithdrawal(
    customerId: CustomerId,
    fieldName: String,
    field: String?,
    minLength: Int,
    maxLength: Int
  ) = result {
    field?.let {
      if (it.length < minLength) {
        raise(InvalidNoteError("$fieldName must be at least $minLength characters long"))
      }
      if (it.length > maxLength) {
        raise(InvalidNoteError("$fieldName exceeds the maximum length of $maxLength characters"))
      }
      if (it.any { char -> char.isISOControl() }) {
        raise(InvalidNoteError("$fieldName contains invalid control characters"))
      }
      it
    } ?: raise(ParameterIsRequired(customerId, fieldName))
  }

  companion object {
    const val MAX_NOTE_LENGTH = 400
    const val MAX_WITHDRAWAL_REASON_LENGTH = 400
    const val MAX_ATTESTATION_DESTINATION_LENGTH = 255
  }
}

sealed class ValidationError :
  Exception(),
    InfoOnly

data class AmountTooLow(val customerId: CustomerId, val amount: Bitcoins, val minimum: Bitcoins) :
  ValidationError()

data class ParameterIsRequired(val customerId: CustomerId, val parameter: String) :
  ValidationError()

/**
 * Error thrown when a withdrawal cannot be processed due to insufficient balance or minimum
 * amount requirements.
 * @param currentBalance The user's current balance
 * @param requiredMinimumBalance The minimum balance required to qualify for any withdrawal
 * @param availableBalanceAfterMinFee The actual amount available for withdrawal after minimum fee
 * @param requestedAmount The amount the user attempted to withdraw
 */
data class InsufficientBalance(
  val currentBalance: Bitcoins? = null,
  val requiredMinimumBalance: Bitcoins? = null,
  val availableBalanceAfterMinFee: Bitcoins? = null,
  val requestedAmount: Bitcoins? = null,
) : ValidationError()

data class InvalidNoteError(override val message: String) : ValidationError()

data class InvalidSourceBalanceToken(
  val customerId: CustomerId,
  val sourceBalanceToken: BalanceId
) : ValidationError()

data class InvalidBlockTarget(val blockTarget: Int) : ValidationError()

data class AmountBelowFreeTier(
  val customerId: CustomerId,
  val amount: Bitcoins,
  val freeTierMinimum: Bitcoins
) : ValidationError()
