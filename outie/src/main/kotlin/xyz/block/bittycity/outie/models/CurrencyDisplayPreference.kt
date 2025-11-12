package xyz.block.bittycity.outie.models

import xyz.block.bittycity.common.models.BitcoinDisplayUnits

data class CurrencyDisplayPreference(
  val currencyDisplayUnits: CurrencyDisplayUnits,
  val bitcoinDisplayUnits: BitcoinDisplayUnits,
)
