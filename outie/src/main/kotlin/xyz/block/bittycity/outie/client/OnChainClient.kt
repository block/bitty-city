package xyz.block.bittycity.outie.client

import app.cash.quiver.extensions.catch
import arrow.core.raise.result
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.common.models.Bitcoins
import org.bitcoinj.base.Address

/**
 * Interface for the on-chain service responsible for submitting withdrawal requests to the blockchain
 * and tracking their progress.
 */
interface OnChainClient {

  /**
   * Submits a withdrawal request to the blockchain.
   *
   * @param request The [WithdrawRequest] containing all required fields.
   * @return a [Result] containing the [WithdrawResponse] if successful.
   */
  fun submitWithdrawal(request: WithdrawRequest): Result<WithdrawResponse>
}

/**
 * Data class representing the request to submit a withdrawal to the blockchain.
 *
 * All fields are required unless otherwise specified.
 *
 * Note: Both the [amount] and [fee] are denominated in BTC.
 *
 * @property withdrawalToken A unique client-specified token for this request.
 * @property customerId The initiating customer's ID.
 * @property destinationAddress The destination address of the withdrawal.
 * @property amount The total amount to be sent, denominated in BTC.
 * @property fee The fee charged for the withdrawal, denominated in BTC.
 * @property blockTarget The desired block target for processing the withdrawal.
 * @property metadata A list of key-value pairs associated with the withdrawal.
 */
data class WithdrawRequest(
  val withdrawalToken: WithdrawalToken,
  val customerId: CustomerId,
  val destinationAddress: Address,
  val amount: Bitcoins,
  val fee: Bitcoins,
  val speed: WithdrawalSpeed,
  val metadata: Map<String, String>
) {
  init {
    require(amount.units > 0) { "Amount must be greater than zero" }
    require(fee.units >= 0) { "Fee must be non-negative" }
  }

  companion object {
    fun Withdrawal.toWithdrawalRequest(): Result<WithdrawRequest> = result {
      val targetWallet =
        targetWalletAddress ?: raise(IllegalArgumentException("Target wallet address is required"))
      val withdrawalAmount = amount ?: raise(IllegalArgumentException("Amount is required"))
      val selectedSpeed =
        selectedSpeed ?: raise(IllegalArgumentException("Selected speed is required"))
      val withdrawalFee = selectedSpeed.totalFee

      Result.catch {
        WithdrawRequest(
          withdrawalToken = id,
          customerId = customerId,
          destinationAddress = targetWallet,
          amount = withdrawalAmount,
          fee = withdrawalFee,
          speed = selectedSpeed.speed,
          metadata = emptyMap()
        )
      }.bind()
    }
  }
}

/**
 * Data class representing the response from the blockchain for a withdrawal request.
 *
 * @property withdrawalToken The token returned acknowledging the recorded withdrawal.
 * @property state The initial state of the withdrawal, which will always be PAYMENT_STATE_PENDING.
 */
data class WithdrawResponse(val withdrawalToken: WithdrawalToken, val state: OnChainState)

/**
 * Enum representing the possible payment states of an on-chain withdrawal.
 *
 * For on-chain withdrawals, the progression is:
 * - SubmissionHeld
 * - Submitted
 * - AwaitingConfirmation
 * - ConfirmedOnChain
 */
enum class OnChainState {
  SubmissionHeld,
  Submitted,
  AwaitingConfirmation,
  ConfirmedOnChain
}
