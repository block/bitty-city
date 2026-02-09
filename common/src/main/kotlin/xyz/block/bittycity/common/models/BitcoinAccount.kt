package xyz.block.bittycity.common.models

data class BitcoinAccount(
  val customerId: CustomerId,
  val balanceId: BalanceId,
  val currencyDisplayPreference: CurrencyDisplayPreference
)
