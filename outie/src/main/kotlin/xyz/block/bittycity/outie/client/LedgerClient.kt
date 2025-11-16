package xyz.block.bittycity.outie.client

import xyz.block.bittycity.outie.models.BalanceId
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.common.models.LedgerTransactionId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.domainapi.InfoOnly

/**
 * Interface for a service that handles ledgering operations for on-chain withdrawals.
 *
 * This service is responsible for:
 * - Fetching the user's current ledger balance.
 * - Creating a ledger transaction for a withdrawal request.
 * - Confirming that the transaction has been successfully processed on-chain.
 * - Voiding a transaction if the withdrawal fails or is cancelled.
 *
 * Note: Both the withdrawal amount and fees are denominated in BTC.
 */
interface LedgerClient {

  /**
   * Retrieves the ledger balance for a given user.
   *
   * @param customerId The ID of the user.
   * @param balanceId The ID representing the user's stored balance.
   * @return a [Result] containing the [Bitcoins] balance if the operation is successful.
   */
  fun getBalance(customerId: CustomerId, balanceId: BalanceId): Result<Bitcoins>

  /**
   * Creates a ledger transaction for an on-chain withdrawal.
   *
   * @param withdrawal The withdrawal object.
   * @return a [Result] containing the ledger [LedgerTransactionId] for the transaction if successful.
   */
  fun createTransaction(withdrawal: Withdrawal): Result<LedgerTransactionId>

  /**
   * Confirms a ledger transaction after the blockchain confirms the withdrawal.
   *
   * @param customerId The ID of the user initiating the withdrawal.
   * @param balanceId The ID representing the user's stored balance.
   * @param ledgerTransactionId The ID identifying the ledger transaction.
   * @return a [Result] indicating whether the confirmation was successful.
   */
  fun confirmTransaction(
    customerId: CustomerId,
    balanceId: BalanceId,
    ledgerTransactionId: LedgerTransactionId
  ): Result<Unit>

  /**
   * Voids a ledger transaction if the withdrawal is cancelled or rejected.
   *
   * @param customerId The ID of the user initiating the withdrawal.
   * @param balanceId The ID representing the user's stored balance.
   * @param ledgerTransactionId The ID identifying the ledger transaction.
   * @return a [Result] indicating whether the voiding operation was successful.
   */
  fun voidTransaction(
    customerId: CustomerId,
    balanceId: BalanceId,
    ledgerTransactionId: LedgerTransactionId
  ): Result<Unit>

  /**
   * Updates a ledger transaction and refunds the fee. This is needed when a withdrawal is held for
   * interdiction because we can no longer meet the SLAs associated with the fee.
   *
   * @param withdrawal The withdrawal object.
   * @return a [Result] containing the ledger [LedgerTransactionId] for the transaction because it
   * might be different (depending on how the underlying ledger works).
   */
  fun refundFee(withdrawal: Withdrawal): Result<LedgerTransactionId>

  /**
   * Freezes the funds in the withdrawal. This typically involves debiting the funds from the user
   * account and crediting them to a sanctions account.
   *
   *  @param withdrawal The withdrawal object.
   *  @return a [Result] indicating whether the funds were frozen successfully.
   */
  fun freezeFunds(withdrawal: Withdrawal): Result<Unit>
}

sealed class LedgerError: Exception()
data object IdempotencyKeyReused: LedgerError()
data object TransactionAlreadyCompleted: LedgerError()
data object TransactionDoesNotExist : LedgerError()
data object InsufficientFunds: LedgerError(), InfoOnly
data class InternalServerError(override val message: String): LedgerError()
data class InvalidRequest(override val message: String): LedgerError()
