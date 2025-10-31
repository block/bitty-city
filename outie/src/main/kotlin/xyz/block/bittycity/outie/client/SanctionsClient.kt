package xyz.block.bittycity.outie.client

import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.models.Bitcoins
import org.bitcoinj.base.Address

/**
 * Evaluate sanctions for a withdrawal.
 */
interface SanctionsClient {
  /**
   * Evaluates sanctions for a withdrawal.
   *
   * @param event The withdrawal event.
   */
  fun evaluateSanctions(
    customerId: String,
    withdrawalToken: WithdrawalToken,
    targetWalletAddress: Address,
    amount: Bitcoins?
  ): Result<Evaluation>
}

/**
 * Represents the sanctions result.
 */
enum class Evaluation {
  APPROVE, // Withdrawal is fine, continue
  HOLD, // Withdrawal has been flagged for manual checks
  FAIL // The sanctions screening process failed
}
