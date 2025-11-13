package xyz.block.bittycity.common.models

/**
 * Represents a fee that is charged by the on-chain withdrawal service on top of the raw fee, which
 * is the cost of submitting the withdrawal on-chain within the chosen SLAs.
 */
sealed class ServiceFee(open val value: Bitcoins)

/**
 * A simple flat fee that is defined in the customer's fiat currency and converted to sats before
 * adding to the raw fee.
 */
data class FlatFee(override val value: Bitcoins) : ServiceFee(value)

/**
 * A fee that is calculated as a margin of the raw fee.
 */
data class MarginFee(val margin: Int, override val value: Bitcoins) : ServiceFee(value)
