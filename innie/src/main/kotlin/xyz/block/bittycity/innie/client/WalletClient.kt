package xyz.block.bittycity.innie.client

import org.bitcoinj.base.Address
import xyz.block.bittycity.common.models.CustomerId

interface WalletClient {
  fun lookupWallet(address: Address): Result<CustomerId?>

  /**
   * Rotates the wallet address for a given customer.
   *
   * @param customerId The ID of the customer whose wallet address should be rotated.
   * @return a [Result] containing the new wallet [Address] if successful.
   */
  fun rotateWalletAddress(customerId: CustomerId): Result<Address>
}
