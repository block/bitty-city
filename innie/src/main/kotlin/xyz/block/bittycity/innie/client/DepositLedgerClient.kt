package xyz.block.bittycity.innie.client

import org.bitcoinj.base.Address
import org.joda.money.Money
import xyz.block.bittycity.common.models.BalanceId
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.common.models.LedgerTransactionId
import xyz.block.bittycity.innie.models.DepositReversalToken
import xyz.block.bittycity.innie.models.DepositToken
import java.time.Instant

interface DepositLedgerClient {
  /**
   * Confirms a ledger transaction after the blockchain confirms a deposit.
   *
   * @param customerId The ID of the user receiving the deposit.
   * @param balanceId The ID representing the user's stored balance.
   * @param ledgerTransactionId The ID identifying the ledger transaction.
   * @return a [Result] indicating whether the confirmation was successful.
   */
  fun confirmDepositTransaction(
    customerId: CustomerId,
    balanceId: BalanceId,
    ledgerTransactionId: LedgerTransactionId
  ): Result<Unit>

  /**
   * Confirms a ledger transaction after the blockchain confirms a deposit reversal.
   *
   * @param customerId The ID of the user initiating the reversal.
   * @param balanceId The ID representing the user's stored balance.
   * @param ledgerTransactionId The ID identifying the ledger transaction.
   * @return a [Result] indicating whether the confirmation was successful.
   */
  fun confirmReversalTransaction(
    customerId: CustomerId,
    balanceId: BalanceId,
    ledgerTransactionId: LedgerTransactionId
  ): Result<Unit>

  /**
   *  Creates a ledger transaction for an on-chain deposit.
   *
   *  @param depositId The ID of the deposit.
   *  @param customerId The ID of the user receiving the deposit.
   *  @param balanceId The ID representing the user's stored balance.
   *  @param createdAt The date when the deposit was created.
   *  @param amount The deposit amount in bitcoin.
   *  @param fiatEquivalent The equivalent amount in fiat.
   *  @return a [Result] containing the ledger [LedgerTransactionId] for the transaction if successful.
   */
  fun createDepositTransaction(
    depositId: DepositToken,
    customerId: CustomerId,
    balanceId: BalanceId,
    createdAt: Instant,
    amount: Bitcoins,
    fiatEquivalent: Money
  ): Result<LedgerTransactionId>

  /**
   * Voids a ledger transaction if the deposit fails on-chain.
   *
   * @param customerId The ID of the user receiving the deposit.
   * @param balanceId The ID representing the user's stored balance.
   * @param ledgerTransactionId The ID identifying the ledger transaction.
   * @return a [Result] indicating whether the voiding operation was successful.
   */
  fun voidDepositTransaction(
    customerId: CustomerId,
    balanceId: BalanceId,
    ledgerTransactionId: LedgerTransactionId
  ): Result<Unit>

  /**
   * Freezes the funds in the reversal. This typically involves debiting the deposit funds that were previously reserved
   * and crediting them to a sanctions account.
   *
   *  @param depositReversalId The ID of the reversal.
   *  @param customerId The ID of the user receiving the deposit.
   *  @param balanceId The ID representing the user's stored balance.
   *  @param createdAt The date when the deposit was created.
   *  @param amount The deposit amount in bitcoin.
   *  @param fiatEquivalent The equivalent amount in fiat.
   *  @param targetWalletAddress The target wallet address of the reversal.
   *  @return a [Result] indicating whether the funds were frozen successfully.
   */
  fun freezeFunds(
    depositReversalId: DepositReversalToken,
    customerId: CustomerId,
    balanceId: BalanceId,
    createdAt: Instant,
    amount: Bitcoins,
    fiatEquivalent: Money,
    targetWalletAddress: Address
  ): Result<Unit>
}
