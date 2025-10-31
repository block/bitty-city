package xyz.block.bittycity.outie.testing

import xyz.block.bittycity.outie.models.Bitcoins
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.outie.models.CustomerId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import java.time.Instant

class TestDataGenerator @Inject constructor(private val withdrawalStore: WithdrawalStore) {

  @Suppress("LongParameterList")
  fun seedWithdrawal(
    id: WithdrawalToken = Arbitrary.withdrawalToken.next(),
    state: WithdrawalState = CollectingInfo,
    customerId: CustomerId = Arbitrary.customerId.next(),
    amount: Bitcoins = Arbitrary.bitcoins.next(),
    createdAt: Instant? = null,
    exchangeRate: Money = Arbitrary.exchangeRate.next(),
    amountUsdWholeDollars: Long? = null,
  ) = withdrawalStore.insertWithdrawal(
    withdrawalToken = id,
    customerId = customerId,
    sourceBalanceToken = Arbitrary.balanceId.next(),
    state = state,
    source = "BITTY",
    targetWalletAddress = Arbitrary.walletAddress.next(),
    amount = amountUsdWholeDollars?.let {
      Withdrawal.usdToSatoshi(Money.ofMajor(CurrencyUnit.USD, it), exchangeRate)
    } ?: amount,
    createdAt = createdAt,
    exchangeRate = exchangeRate
  ).getOrThrow()
}
