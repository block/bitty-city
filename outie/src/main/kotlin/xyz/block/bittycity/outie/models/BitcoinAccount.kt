package xyz.block.bittycity.outie.models

data class BitcoinAccount(
  val customerId: CustomerId,
  val balanceId: BalanceId,
  val currencyDisplayPreference: CurrencyDisplayPreference
)
