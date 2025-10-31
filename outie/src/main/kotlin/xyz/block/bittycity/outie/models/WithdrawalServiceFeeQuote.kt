package xyz.block.bittycity.outie.models

/**
 * Represents a quote for the fee that the service charges in addition to the raw fee (the cost of
 * submitting the withdrawal on-chain within the predefined SLAs).
 *
 * @property speed The speed for this fee quote.
 * @property fee The service fee. Can be a flat fee or based on a margin.
 */
data class WithdrawalServiceFeeQuote(val speed: WithdrawalSpeed, val fee: ServiceFee)
