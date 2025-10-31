package xyz.block.bittycity.outie.testing

import arrow.core.raise.result
import xyz.block.bittycity.outie.client.ExchangeQuote
import xyz.block.bittycity.outie.client.ExchangeRateClient
import xyz.block.bittycity.outie.models.Bitcoins
import org.joda.money.CurrencyUnit
import org.joda.money.CurrencyUnit.USD
import org.joda.money.Money

class FakeExchangeRateClient :
  TestFake(),
  ExchangeRateClient {

  var nextExchangeRate: Money by resettable { Money.ofMinor(USD, 100_000_00L) }

  override fun quoteExchange(fiatAmount: Money): Result<ExchangeQuote> = result {
    ExchangeQuote(
      bitcoinAmount = Bitcoins(fiatAmount.amountMinorLong * nextExchangeRate.amountMinorLong),
      fiatAmount = fiatAmount,
      exchangeRate = nextExchangeRate
    )
  }

  override fun quoteExchange(
    bitcoinAmount: Bitcoins,
    fiatCurrency: CurrencyUnit
  ): Result<ExchangeQuote> = result {
    ExchangeQuote(
      bitcoinAmount = bitcoinAmount,
      fiatAmount = Money.ofMinor(USD, bitcoinAmount.units / nextExchangeRate.amountMinorLong),
      exchangeRate = nextExchangeRate
    )
  }
}
