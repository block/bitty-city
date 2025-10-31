package xyz.block.bittycity.outie.models

import xyz.block.bittycity.outie.models.Bitcoins

/**
 * Represents a fee quote for a specific block target and destination address.
 *
 * @property blockTarget The block target for processing the withdrawal.
 * @property fee The estimated on-chain fee for this withdrawal, denominated in BTC.
 */
data class OnchainFeeQuote(val blockTarget: Int, val fee: Bitcoins)
