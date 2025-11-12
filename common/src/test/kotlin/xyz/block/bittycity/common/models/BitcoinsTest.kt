package xyz.block.bittycity.common.models

import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import org.junit.jupiter.api.Test

class BitcoinsTest {

  @Test
  fun `constructor should create Bitcoins with the given units`() {
    val bitcoins = Bitcoins(1000L)
    bitcoins.units shouldBe 1000L
  }

  @Test
  fun `plus operator should add two Bitcoins values`() {
    val bitcoins1 = Bitcoins(1000L)
    val bitcoins2 = Bitcoins(500L)
    val result = bitcoins1 + bitcoins2
    result.units shouldBe 1500L
  }

  @Test
  fun `minus operator should subtract two Bitcoins values`() {
    val bitcoins1 = Bitcoins(1000L)
    val bitcoins2 = Bitcoins(300L)
    val result = bitcoins1 - bitcoins2
    result.units shouldBe 700L
  }

  @Test
  fun `compareTo operator should compare Bitcoins values correctly`() {
    val bitcoins1 = Bitcoins(1000L)
    val bitcoins2 = Bitcoins(500L)
    val bitcoins3 = Bitcoins(1000L)

    bitcoins1 shouldBeGreaterThan bitcoins2
    bitcoins2 shouldBeLessThan bitcoins1
    bitcoins1 shouldBe bitcoins3 // Data classes are equal if properties are equal
    bitcoins1.compareTo(bitcoins3) shouldBe 0
  }

  @Test
  fun `CURRENCY_UNIT_BTC should have the correct currency code`() {
    Bitcoins.currency.code shouldBe "BTC"
  }

  @Test
  fun `CURRENCY_UNIT_BTC should have the correct numeric code`() {
    Bitcoins.currency.numericCode shouldBe 0
  }

  @Test
  fun `CURRENCY_UNIT_BTC should have 8 decimal places`() {
    Bitcoins.currency.decimalPlaces shouldBe 8
  }

  @Test
  fun `CURRENCY_UNIT_BTC should be registered as a valid currency`() {
    Bitcoins.currency shouldBe CurrencyUnit.of("BTC")
  }

  @Test
  fun `CURRENCY_UNIT_BTC should be the same instance when retrieved multiple times`() {
    val currency1 = CurrencyUnit.of("BTC")
    val currency2 = CurrencyUnit.of("BTC")
    currency2 shouldBe currency1
  }

  @Test
  fun `CURRENCY_UNIT_BTC should support money operations`() {
    val money = Money.of(Bitcoins.currency, 1.5)
    money.currencyUnit shouldBe Bitcoins.currency
    money.amount.toDouble() shouldBe 1.5
    money.amountMinorLong shouldBe 150_000_000
  }
}
