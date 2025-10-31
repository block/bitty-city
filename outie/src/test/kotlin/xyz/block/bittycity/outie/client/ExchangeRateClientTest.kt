package xyz.block.bittycity.outie.client

import xyz.block.bittycity.outie.models.Withdrawal
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import org.junit.jupiter.api.Test

class ExchangeRateClientTest {

  @Test
  fun `converting from USD to Satoshis and back results in the same amount`() {
    runTest {
      val usdPricePerBtc: Arb<Long> = Arb.long(95_000_00L..100_500_00L)
      val amount: Arb<Long> = Arb.long(50_00L..60_00L)
      checkAll(usdPricePerBtc, amount) { rate, amount ->
        val exchangeRete = Money.ofMinor(CurrencyUnit.USD, rate)
        val amountToConvert = Money.ofMinor(CurrencyUnit.USD, amount)
        val satsEquivalent = Withdrawal.usdToSatoshi(amountToConvert, exchangeRete)
        val convertedAmount = Withdrawal.satoshiToUsd(satsEquivalent, exchangeRete)
        convertedAmount shouldBe amountToConvert
      }
    }
  }
}
