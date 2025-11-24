package xyz.block.bittycity.innie.models

import org.bitcoinj.base.Address
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.innie.models.RequirementId.DEPOSIT_CONFIRMED_ON_CHAIN
import xyz.block.domainapi.Input.ResumeResult

sealed class DepositResumeResult(id: RequirementId) : ResumeResult<RequirementId>(id) {
  data class ConfirmedOnChain(
    val depositToken: DepositToken,
    val paymentToken: String,
    val targetWalletAddress: Address,
    val amount: Bitcoins,
    val blockchainTransactionId: String,
    val blockchainTransactionOutputIndex: Int
  ) : DepositResumeResult(DEPOSIT_CONFIRMED_ON_CHAIN)
}
