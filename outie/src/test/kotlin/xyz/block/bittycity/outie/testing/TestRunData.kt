package xyz.block.bittycity.outie.testing

import xyz.block.bittycity.outie.models.BalanceId
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.models.WithdrawalToken
import org.bitcoinj.base.Address
import org.joda.money.Money
import java.time.Instant

data class TestRunData(
  val withdrawalToken: WithdrawalToken,
  val customerToken: CustomerId,
  val balanceId: BalanceId,
  val targetWalletAddress: Address,
  val newWalletAddress: Address,
  val bitcoins: Bitcoins,
  val fee: Bitcoins,
  val speed: WithdrawalSpeed,
  val exchangeRate: Money
) {
  val newWithdrawal: Withdrawal by lazy {
    Withdrawal(
      id = withdrawalToken,
      createdAt = Instant.now(),
      updatedAt = Instant.now(),
      version = 1L,
      customerId = customerToken,
      state = CollectingInfo,
      sourceBalanceToken = balanceId,
      source = "BITTY",
      targetWalletAddress = targetWalletAddress,
      amount = bitcoins,
      exchangeRate = exchangeRate
    )
  }
}
