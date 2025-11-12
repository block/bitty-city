package xyz.block.bittycity.outie.models

import xyz.block.bittycity.outie.models.RequirementId.AMOUNT
import xyz.block.bittycity.outie.models.RequirementId.CONFIRMED_ON_CHAIN
import xyz.block.bittycity.outie.models.RequirementId.FAILED_ON_CHAIN
import xyz.block.bittycity.outie.models.RequirementId.NOTE
import xyz.block.bittycity.outie.models.RequirementId.OBSERVED_IN_MEMPOOL
import xyz.block.bittycity.outie.models.RequirementId.SANCTIONS_HELD
import xyz.block.bittycity.outie.models.RequirementId.SANCTIONS_WITHDRAWAL_REASON
import xyz.block.bittycity.outie.models.RequirementId.SCAM_WARNING
import xyz.block.bittycity.outie.models.RequirementId.SCAM_WARNING_CANCELLED
import xyz.block.bittycity.outie.models.RequirementId.SELF_ATTESTATION
import xyz.block.bittycity.outie.models.RequirementId.SPEED
import xyz.block.bittycity.outie.models.RequirementId.SUBMITTED_ON_CHAIN
import xyz.block.bittycity.outie.models.RequirementId.TARGET_WALLET_ADDRESS
import xyz.block.bittycity.outie.models.RequirementId.USER_CONFIRMATION
import org.bitcoinj.base.Address
import org.joda.money.Money
import xyz.block.bittycity.common.models.BitcoinDisplayUnits
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.domainapi.Input
import xyz.block.domainapi.Input.HurdleResponse
import xyz.block.domainapi.ResultCode
import xyz.block.domainapi.UserInteraction.Hurdle
import xyz.block.domainapi.UserInteraction.Notification
import kotlin.time.Duration

sealed class WithdrawalHurdle(id: RequirementId) : Hurdle<RequirementId>(id) {

  /**
   * Hurdle used to capture a target wallet address.
   */
  data class TargetWalletAddressHurdle(val previousWalletAddress: String? = null,) :
    WithdrawalHurdle(TARGET_WALLET_ADDRESS)

  /**
   * Hurdle to capture the amount of Bitcoin to withdraw.
   *
   * @param currentBalance The current balance. Used to prevent entering an invalid amount.
   * @param displayUnits The user's preferred currency display units.
   * @param targetWalletAddress The wallet address into which the funds will be sent, if it has been
   * entered.
   * @param minimumAmount The minimum amount that can be withdrawn. Used to prevent entering an
   * invalid amount.
   * @param maximumAmount The maximum amount that can be withdrawn. Used to prevent entering an
   * invalid amount.
   * @param maximumAmountReason The reason why this is the maximum amount the user can withdraw.
   * @param exchangeRate The exchange rate. This is fixed at the time the withdrawal is created.
   */
  data class AmountHurdle(
    val currentBalance: Bitcoins,
    val displayUnits: CurrencyDisplayPreference,
    val targetWalletAddress: Address? = null,
    val minimumAmount: Bitcoins,
    val maximumAmount: Bitcoins,
    val maximumAmountFiatEquivalent: Money,
    val maximumAmountReason: MaximumAmountReason,
    val exchangeRate: Money,
    val previousUserAmount: Bitcoins? = null
  ) : WithdrawalHurdle(AMOUNT)

  enum class MaximumAmountReason { BALANCE, DAILY_LIMIT, WEEKLY_LIMIT }

  /**
   * Hurdle used to capture a note associated with the withdrawal.
   */
  data class NoteHurdle(
    val maxNoteLength: Int,
    val previousNote: String? = null,
  ) : WithdrawalHurdle(NOTE)

  /**
   * Hurdle used to indicate that the user should be warned about a potential scam.
   */
  data object ScamWarningHurdle : WithdrawalHurdle(SCAM_WARNING)

  /**
   * Hurdle used to indicate that the user has selected a withdrawal speed.
   *
   * @param withdrawalSpeedOptions List of available withdrawal speed options.
   * @param currentBalance The user's current balance. Needed for the client to calculate if a speed
   * option is selectable or not, if this information is not populated by the server (can happen if
   * multiple hurdles are returned simultaneously and the client wants to process them all without
   * going back to the server).
   * @param freeTierMinAmount The minimum amount that can be withdrawn using the free tier. Needed to
   * determine if the free tier is selectable when this information is not populated by the server.
   *
   */
  data class SpeedHurdle(
    val withdrawalSpeedOptions: List<WithdrawalSpeedOption>,
    val currentBalance: Bitcoins,
    val freeTierMinAmount: Bitcoins,
    val displayUnits: BitcoinDisplayUnits,
  ) : WithdrawalHurdle(SPEED)

  data class ConfirmationHurdle(
    val selectedSpeed: WithdrawalSpeedOption?,
    val walletAddress: Address?,
    val amount: Bitcoins?,
    val fiatEquivalent: Money?,
    val totalFee: Bitcoins?,
    val totalFeeFiatEquivalent: Money?,
    val total: Bitcoins?,
    val displayUnits: BitcoinDisplayUnits,
    val note: String?,
    val currentBalance: Bitcoins,
    val exchangeRate: Money?
  ) : WithdrawalHurdle(USER_CONFIRMATION)

  /**
   * Hurdle used to indicate that the withdrawal has been held for manual sanctions checks and the
   * reason for the withdrawal should be collected.
   */
  data class WithdrawalReasonHurdle(
    val maxWithdrawalReasonLength: Int
  ) : WithdrawalHurdle(SANCTIONS_WITHDRAWAL_REASON)

  /**
   * Hurdle used to indicate that the withdrawal needs to complete self-attestations
   * about the recipient address
   */
  data object SelfAttestationHurdle : WithdrawalHurdle(SELF_ATTESTATION)
}

sealed class WithdrawalHurdleResponse(id: RequirementId, code: ResultCode) :
  HurdleResponse<RequirementId>(id, code) {

  /**
   * Hurdle result with the target wallet address. The address should be present if the hurdle
   * was cleared successfully (i.e. result is [ResultCode.CLEARED]).
   */
  data class TargetWalletAddressHurdleResponse(
    val code: ResultCode,
    val walletAddress: String? = null,
  ) : WithdrawalHurdleResponse(TARGET_WALLET_ADDRESS, code)

  /**
   * Hurdle result with the amount entered by the user. The amount should be present if the hurdle
   * was cleared successfully (i.e. result is [ResultCode.CLEARED]).
   *
   * @param result The result of the hurdle.
   * @param userAmount The amount entered by the user, if any.
   */
  data class AmountHurdleResponse(val code: ResultCode, val userAmount: Bitcoins? = null) :
    WithdrawalHurdleResponse(AMOUNT, code)

  /**
   * Hurdle result with the note entered by the user. The note should be present if the hurdle was
   * cleared successfully (i.e. result is [ResultCode.CLEARED]).
   *
   * @param result The result of the hurdle.
   * @param note The note entered by the user, if any.
   */
  data class NoteHurdleResponse(val code: ResultCode, val note: String? = null) :
    WithdrawalHurdleResponse(NOTE, code)

  /**
   * Hurdle result indicating if the user accepted the risk or not. If the user accepted the risk then
   * the result will be [ResultCode.CLEARED]. If the user did not accept the risk then the
   * result will be [ResultCode.FAILED].
   */
  data class ScamWarningHurdleResponse(val code: ResultCode) :
    WithdrawalHurdleResponse(SCAM_WARNING, code)

  /**
   * Hurdle result with the speed selected by the user. The speed should be present if the hurdle was
   * cleared successfully (i.e. result is [ResultCode.CLEARED]).
   * Otherwise, result will be [ResultCode.FAILED].
   *
   * @param result The result of the hurdle.
   */
  data class SpeedHurdleResponse(val code: ResultCode, val selectedSpeed: WithdrawalSpeed? = null) :
    WithdrawalHurdleResponse(SPEED, code)

  data class ConfirmationHurdleResponse(val code: ResultCode) :
    WithdrawalHurdleResponse(USER_CONFIRMATION, code)

  /**
   * Response with the reason for the withdrawal, e.g., "Help a friend".
   */
  data class WithdrawalReasonHurdleResponse(val code: ResultCode, val reason: String? = null) :
    WithdrawalHurdleResponse(SANCTIONS_WITHDRAWAL_REASON, code)

  /**
   * Response with the attestation destination for the withdrawal, e.g., "Coinbase, Kraken, Self-custody".
   */
  data class SelfAttestationHurdleResponse(val code: ResultCode, val destination: String? = null) :
    WithdrawalHurdleResponse(SELF_ATTESTATION, code)
}

sealed class WithdrawalNotification(val requirementId: RequirementId) :
  Notification<RequirementId>(requirementId) {
  /**
   * Notification used to indicate that the user canceled the withdrawal due to the scam warning.
   */
  data object WithdrawalCancelledNotification : WithdrawalNotification(SCAM_WARNING_CANCELLED)

  /**
   * Notification to inform the user that their withdrawal has been held for sanctions and will be
   * manually reviewed.
   */
  data object WithdrawalSanctionsHeld : WithdrawalNotification(SANCTIONS_HELD)

  /**
   * The final screen that is shown to the user when the interaction ends via the happy path.
   */
  data class SubmittedOnChainNotification(
    val amount: Bitcoins,
    val displayUnits: BitcoinDisplayUnits,
    val fiatEquivalent: Money?,
    val approximateWaitTime: Duration,
    val targetWalletAddress: Address,
  ) : WithdrawalNotification(SUBMITTED_ON_CHAIN)
}

/**
 * Resume result that contains the decision of a manual sanctions review.
 */
data class SanctionsHeldDecision(val decision: SanctionsReviewDecision) :
  Input.ResumeResult<RequirementId>(SANCTIONS_HELD)

enum class SanctionsReviewDecision {
  APPROVE,
  DECLINE,
  FREEZE
}

/**
 * Resume result that indicates that an on-chain withdrawal has been observed in the mempool and
 * contains the blockchain transaction id.
 */
data class ObservedInMempool(
  val blockchainTransactionId: String,
  val blockchainTransactionOutputIndex: Int
) : Input.ResumeResult<RequirementId>(OBSERVED_IN_MEMPOOL)

/**
 * Resume result that indicates that an on-chain withdrawal has been confirmed on-chain and contains
 * the blockchain transaction id.
 */
data class ConfirmedOnChain(
  val blockchainTransactionId: String,
  val blockchainTransactionOutputIndex: Int
) : Input.ResumeResult<RequirementId>(CONFIRMED_ON_CHAIN)

/**
 * Resume result that indicates that an on-chain withdrawal has failed. This generally shouldn't
 * happen because there are mechanisms in place in Bitty to avoid most common error conditions, such
 * as dusty withdrawals (transaction amount too small) or invalid requests (target wallet address on
 * the wrong network).
 */
data class FailedOnChain(
  val blockchainTransactionId: String?,
  val blockchainTransactionOutputIndex: Int?
) : Input.ResumeResult<RequirementId>(FAILED_ON_CHAIN)
