package xyz.block.bittycity.common.client

import org.bitcoinj.base.Address
import xyz.block.bittycity.common.models.Bitcoins

/**
 * Evaluate sanctions for a transaction.
 *
 * @param T The type of the transaction identifier (e.g., WithdrawalToken, DepositToken).
 */
interface SanctionsClient<T> {
  /**
   * Evaluates sanctions for a transaction.
   *
   * @param customerId The customer identifier.
   * @param transactionToken The transaction token.
   * @param targetWalletAddress The target wallet address.
   * @param amount The amount in Bitcoins.
   */
  fun evaluateSanctions(
    customerId: String,
    transactionToken: T,
    targetWalletAddress: Address,
    amount: Bitcoins?
  ): Result<Evaluation>
}

/**
 * Represents the sanctions result.
 */
enum class Evaluation {
  APPROVE,
  HOLD,
  FAIL
}
