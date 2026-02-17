package xyz.block.bittycity.innie.models

import org.bitcoinj.base.Address
import java.time.Instant
import xyz.block.bittycity.common.models.LedgerTransactionId
import xyz.block.bittycity.common.models.ServiceFee

data class DepositReversal(
  /** The identifier of the deposit reversal. */
  val token: DepositReversalToken,

  /** The time in epoch millis that this reversal was created. */
  val createdAt: Instant? = null,

  /** The time in epoch millis that this reversal was updated. */
  val updatedAt: Instant? = null,

  /** Each time the reversal's state is updated, the version increments. Zero-based. */
  val version: Long? = null,

  /** The wallet address where this reversal will be sent to. */
  val targetWalletAddress: Address? = null,

  /** The cost to us of submitting the reversal on-chain. */
  val serviceFee: ServiceFee? = null,

  /** The reason attributed to the failure of this reversal, if applicable. */
  val failureReason: DepositReversalFailureReason? = null,

  /** The blockchain transaction id hash (if any) for this reversal. */
  val blockchainTransactionId: String? = null,

  /** The ledger transaction id used to settle this reversal. */
  val ledgerTransactionId: LedgerTransactionId? = null,

  /** The index of the output in the transaction. */
  val blockchainTransactionOutputIndex: Int? = null,

  /** Records the user decision if a scam warning was presented to them. */
  val userHasAcceptedRisk: Boolean? = null,

  /** Records the user's confirmation of the details of the reversal. */
  val userHasConfirmed: Boolean? = null,

  /**
   * If a reversal is flagged and held for sanctions the user must enter the reason for the
   * reversal to assist compliance agents in deciding the final outcome.
   */
  val reasonForReversal: String? = null,
)
