package xyz.block.bittycity.innie.testing

import org.bitcoinj.base.Address
import org.joda.money.Money
import xyz.block.bittycity.common.models.BalanceId
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.WaitingForDepositConfirmedOnChainStatus
import java.time.Instant

data class TestRunData(
  val depositToken: DepositToken,
  val customerToken: CustomerId,
  val balanceId: BalanceId,
  val bitcoins: Bitcoins,
  val exchangeRate: Money,
  val targetWalletAddress: Address,
  val blockchainTransactionId: String,
  val blockchainTransactionOutputIndex: Int,
  val paymentToken: String
) {
  val newDeposit: Deposit by lazy {
    Deposit(
      id = depositToken,
      state = WaitingForDepositConfirmedOnChainStatus,
      createdAt = Instant.now(),
      updatedAt = Instant.now(),
      version = 1L,
      customerId = customerToken,
      amount = bitcoins,
      exchangeRate = exchangeRate,
      targetWalletAddress = targetWalletAddress,
      blockchainTransactionId = blockchainTransactionId,
      blockchainTransactionOutputIndex = blockchainTransactionOutputIndex,
      paymentToken = paymentToken,
      targetBalanceToken =  balanceId,
    )
  }
}
