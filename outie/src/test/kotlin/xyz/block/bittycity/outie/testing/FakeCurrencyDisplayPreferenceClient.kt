package xyz.block.bittycity.outie.testing

import xyz.block.bittycity.common.testing.TestFake

import app.cash.quiver.extensions.success
import xyz.block.bittycity.outie.client.CurrencyDisplayPreferenceClient
import xyz.block.bittycity.common.models.BitcoinDisplayUnits
import xyz.block.bittycity.outie.models.CurrencyDisplayPreference
import xyz.block.bittycity.outie.models.CurrencyDisplayUnits

class FakeCurrencyDisplayPreferenceClient :
  TestFake(),
  CurrencyDisplayPreferenceClient {

  var nextCurrencyDisplayPreference by resettable {
    CurrencyDisplayPreference(
      currencyDisplayUnits = CurrencyDisplayUnits.USD,
      bitcoinDisplayUnits = BitcoinDisplayUnits.SATOSHIS
    ).success()
  }

  override fun getCurrencyDisplayPreference(customerId: String): Result<CurrencyDisplayPreference> =
    nextCurrencyDisplayPreference
}
