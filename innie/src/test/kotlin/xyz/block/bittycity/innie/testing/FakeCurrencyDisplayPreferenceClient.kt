package xyz.block.bittycity.innie.testing

import xyz.block.bittycity.common.testing.TestFake

import app.cash.quiver.extensions.success
import xyz.block.bittycity.common.client.CurrencyDisplayPreferenceClient
import xyz.block.bittycity.common.models.BitcoinDisplayUnits
import xyz.block.bittycity.common.models.CurrencyDisplayPreference
import xyz.block.bittycity.common.models.CurrencyDisplayUnits

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
