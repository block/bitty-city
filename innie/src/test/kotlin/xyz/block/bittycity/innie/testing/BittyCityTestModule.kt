package xyz.block.bittycity.innie.testing

import com.google.inject.Scopes
import org.bitcoinj.base.BitcoinNetwork
import xyz.block.bittycity.common.utils.WalletAddressParser
import xyz.block.bittycity.innie.InnieModule

object BittyCityTestModule : InnieModule() {

  override fun installValidationModule() {
    bind(WalletAddressParser::class.java).`in`(Scopes.SINGLETON)
    bind(BitcoinNetwork::class.java).toInstance(BitcoinNetwork.TESTNET)
  }
}
