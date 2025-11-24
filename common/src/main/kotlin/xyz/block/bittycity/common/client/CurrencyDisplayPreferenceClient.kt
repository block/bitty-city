package xyz.block.bittycity.common.client

import xyz.block.bittycity.common.models.CurrencyDisplayPreference

/**
 * Interface for a service that knows how to find the currency display preferences for a transaction.
 */
interface CurrencyDisplayPreferenceClient {
  fun getCurrencyDisplayPreference(customerId: String): Result<CurrencyDisplayPreference>
}
