package xyz.block.bittycity.outie.client

import xyz.block.bittycity.outie.models.Bitcoins
import org.joda.money.CurrencyUnit
import org.joda.money.Money

interface ExchangeRateClient {
  /**
   * Provide a quote from the given fixed fiat amount to Bitcoin.
   *
   * @param fiatAmount The fixed fiat amount.
   * @return The exchange quote.
   */
  fun quoteExchange(fiatAmount: Money): Result<ExchangeQuote>

  /**
   * Provide a quote from the given Bitcoin amount to a fiat amount.
   *
   * @param bitcoinAmount The Bitcoin amount.
   * @param fiatCurrency The fiat currency.
   * @return The exchange quote.
   */
  fun quoteExchange(bitcoinAmount: Bitcoins, fiatCurrency: CurrencyUnit): Result<ExchangeQuote>
}

/**
 * A quote that captures the equivalence at a point in time of a crypto amount and a fiat amount.
 *
 * @param bitcoinAmount The Bitcoin amount.
 * @param fiatAmount The equivalent fiat amount.
 * @param exchangeRate The rate between the fiat and Bitcoin amounts, expressed in terms of the fiat
 * currency.
 */
data class ExchangeQuote(
  val bitcoinAmount: Bitcoins,
  val fiatAmount: Money,
  val exchangeRate: Money,
) {
  // Internal invariant checking
  init {
    require(fiatAmount.currencyUnit == exchangeRate.currencyUnit) {
      "The exchange rate should be expressed in terms of the fiat currency " +
        "[fiat=${fiatAmount.currencyUnit}][exchange=${exchangeRate.currencyUnit}]"
    }
  }
}
