package xyz.block.bittycity.common.models

import org.joda.money.CurrencyUnit

/**
 * A BIP-177 oriented value type for Bitcoin.
 */
data class Bitcoins(val units: Long) : Comparable<Bitcoins> {
  operator fun plus(other: Bitcoins) = Bitcoins(units + other.units)
  operator fun minus(other: Bitcoins) = Bitcoins(units - other.units)
  override operator fun compareTo(other: Bitcoins) = units.compareTo(other.units)
  fun millisat(): Long = units * MSAT_PER_SAT

  companion object {
    const val MSAT_PER_SAT: Long = 1_000
    const val DECIMAL_PLACES = 8
    const val BITCOINS_PER_BTC = 100_000_000L
    val ZERO = Bitcoins(0)
    val currency: CurrencyUnit by lazy {
      CurrencyUnit.registerCurrency(
        "BTC", // currency code
        0, // numeric code (0 since BTC isn't ISO 4217)
        DECIMAL_PLACES,
        emptyList(), // country codes (none, since BTC isn't tied to a country)
        true // force overwrite if already registered
      )
    }
  }
}
