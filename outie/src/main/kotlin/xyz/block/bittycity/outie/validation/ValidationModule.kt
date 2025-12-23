package xyz.block.bittycity.outie.validation

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Scopes
import jakarta.inject.Singleton
import org.bitcoinj.base.BitcoinNetwork
import xyz.block.bittycity.common.utils.WalletAddressParser

object ValidationModule : AbstractModule() {
  @Provides
  @Singleton
  fun provideMainNetwork(): BitcoinNetwork = BitcoinNetwork.MAINNET

  override fun configure() {
    bind(WalletAddressParser::class.java).`in`(Scopes.SINGLETON)
  }
}
