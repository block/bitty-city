package xyz.block.bittycity.outie.client

import xyz.block.bittycity.outie.models.CurrencyDisplayPreference

/**
 * Interface for a service that knows how to find the currency display preferences for an on-chain
 * withdrawal.
 */
interface CurrencyDisplayPreferenceClient {
  fun getCurrencyDisplayPreference(customerId: String): Result<CurrencyDisplayPreference>
}
