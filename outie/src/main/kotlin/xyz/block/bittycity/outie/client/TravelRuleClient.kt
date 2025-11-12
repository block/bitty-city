package xyz.block.bittycity.outie.client

import xyz.block.bittycity.common.models.CustomerId
import org.bitcoinj.base.Address
import org.joda.money.Money

/**
 * Provides information related to the travel rule requirement.
 */
interface TravelRuleClient {
  /**
   * Returns true if the wallet address requires self attestation for a withdrawal.
   */
  fun requireSelfAttestation(
    targetWalletAddress: Address,
    fiatAmountEquivalent: Money,
    customerId: CustomerId
  ): Result<Boolean>
}
