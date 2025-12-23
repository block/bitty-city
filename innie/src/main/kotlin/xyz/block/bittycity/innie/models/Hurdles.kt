package xyz.block.bittycity.innie.models

import org.bitcoinj.base.Address
import org.joda.money.Money
import xyz.block.bittycity.common.models.BitcoinDisplayUnits
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.domainapi.Input.HurdleResponse
import xyz.block.domainapi.Input.ResumeResult
import xyz.block.domainapi.ResultCode
import xyz.block.domainapi.UserInteraction.Hurdle

sealed class DepositResumeResult(id: RequirementId) : ResumeResult<RequirementId>(id) {
  data class ConfirmedOnChain(
    val depositToken: DepositToken,
    val paymentToken: String,
    val targetWalletAddress: Address,
    val amount: Bitcoins,
    val blockchainTransactionId: String,
    val blockchainTransactionOutputIndex: Int
  ) : DepositResumeResult(RequirementId.DEPOSIT_CONFIRMED_ON_CHAIN)
}

sealed class DepositReversalHurdle(id: RequirementId) : Hurdle<RequirementId>(id) {
  /**
   * Hurdle used to capture a target wallet address.
   */
  data object TargetWalletAddressHurdle : DepositReversalHurdle(RequirementId.REVERSAL_TARGET_WALLET_ADDRESS)

  /**
   * Hurdle to capture the user's confirmation of the reversal details.
   */
  data class ConfirmationHurdle(
    val walletAddress: Address?,
    val amount: Bitcoins?,
    val fiatEquivalent: Money?,
    val displayUnits: BitcoinDisplayUnits
  ) : DepositReversalHurdle(RequirementId.REVERSAL_USER_CONFIRMATION)
}

sealed class DepositReversalHurdleResponse(id: RequirementId, code: ResultCode) :
  HurdleResponse<RequirementId>(id, code) {
  /**
   * Hurdle result with the target wallet address. The address should be present if the hurdle
   * was cleared successfully (i.e. result is [ResultCode.CLEARED]).
   */
  data class TargetWalletAddressHurdleResponse(
    val code: ResultCode,
    val walletAddress: String? = null,
  ) : DepositReversalHurdleResponse(RequirementId.REVERSAL_TARGET_WALLET_ADDRESS, code)

  data class ConfirmationHurdleResponse(val code: ResultCode) :
    DepositReversalHurdleResponse(RequirementId.REVERSAL_USER_CONFIRMATION, code)
}
