package xyz.block.bittycity.common.utils

import org.joda.money.CurrencyUnit
import org.joda.money.Money
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.Bitcoins.Companion.BITCOINS_PER_BTC
import java.math.RoundingMode

object CurrencyConversionUtils {
  fun bitcoinsToUsd(amount: Bitcoins, exchangeRate: Money): Money = Money.ofMinor(
    CurrencyUnit.USD,
    amount.units.toBigDecimal()
      .multiply(exchangeRate.amountMinor)
      .divide(BITCOINS_PER_BTC.toBigDecimal(), 0, RoundingMode.HALF_DOWN)
      .toLong()
  )

  fun usdToBitcoins(usdAmount: Money, exchangeRate: Money): Bitcoins {
    val satoshiValue = usdAmount.amountMinor
      .multiply(BITCOINS_PER_BTC.toBigDecimal())
      .divide(exchangeRate.amountMinor, 0, RoundingMode.HALF_DOWN)
      .toLong()

    return Bitcoins(satoshiValue)
  }
}
