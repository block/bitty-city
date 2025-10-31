package xyz.block.bittycity.outie.validation

import app.cash.quiver.extensions.catch
import jakarta.inject.Inject
import org.bitcoinj.base.Address
import org.bitcoinj.base.AddressParser
import org.bitcoinj.base.BitcoinNetwork

class WalletAddressParser @Inject constructor(
  private val network: BitcoinNetwork
) {

  /** Parses the provided string argument into a wallet address if it is valid for the configured network. */
  fun parse(walletAddress: String): Result<Address> =
    Result.catch {
      AddressParser.getDefault(network).parseAddress(
        walletAddress.replaceFirst(Regex("^bitcoin:"), "").replaceFirst(Regex("\\?.*"), "")
      )
    }
}
