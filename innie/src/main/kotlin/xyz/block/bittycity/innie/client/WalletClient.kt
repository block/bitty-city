package xyz.block.bittycity.innie.client

import org.bitcoinj.base.Address
import xyz.block.bittycity.common.models.CustomerId

interface WalletClient {
  fun lookupWallet(address: Address): Result<CustomerId?>
}
