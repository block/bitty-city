package xyz.block.bittycity.outie.models

import xyz.block.bittycity.common.models.CustomerId

data class BitcoinAccount(
  val customerId: CustomerId,
  val balanceId: BalanceId,
  val currencyDisplayPreference: CurrencyDisplayPreference
)
