package xyz.block.bittycity.innie.models

import org.bitcoinj.base.Address
import java.time.Instant
import xyz.block.bittycity.common.models.ServiceFee

data class DepositReversal(
  /** The identifier of the deposit reversal. */
  val token: DepositToken,

  /** The time in epoch millis that this withdrawal was created. */
  val createdAt: Instant,

  /** The time in epoch millis that this withdrawal was updated. */
  val updatedAt: Instant,

  /** Each time the withdrawal's state is updated, the version increments. Zero-based. */
  val version: Long,

  /** The wallet address where this reversal will be sent to. */
  val targetWalletAddress: Address? = null,

  /** The cost to us of submitting the reversal on-chain. */
  val serviceFee: ServiceFee,

  /** The reason attributed to the failure of this reversal, if applicable. */
  val failureReason: DepositReversalFailureReason,

  /** The blockchain transaction id hash (if any) for this reversal. */
  val blockchainTransactionId: String? = null,

  /** Records the user decision if a scam warning was presented to them. */
  val userHasAcceptedRisk: Boolean? = null,

  /** Records the user's confirmation of the details of the withdrawal. */
  val userHasConfirmed: Boolean? = null,

  /** The index of the output in the transaction. */
  val blockchainTransactionOutputIndex: Int? = null,
)
