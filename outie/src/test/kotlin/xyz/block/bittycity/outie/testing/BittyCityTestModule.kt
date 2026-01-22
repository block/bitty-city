package xyz.block.bittycity.outie.testing

import com.google.inject.Provides
import com.google.inject.Scopes
import org.bitcoinj.base.BitcoinNetwork
import xyz.block.bittycity.outie.OutieModule
import xyz.block.bittycity.outie.store.ResponseOperations
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.outie.store.WithdrawalOperations
import xyz.block.bittycity.common.utils.WalletAddressParser
import xyz.block.bittycity.outie.testing.fakes.FakeResponseOperations
import xyz.block.bittycity.outie.testing.fakes.FakeTransactor
import xyz.block.bittycity.outie.testing.fakes.FakeWithdrawalOperations

object BittyCityTestModule : OutieModule() {

  override fun installValidationModule() {
    bind(WalletAddressParser::class.java).`in`(Scopes.SINGLETON)
    bind(BitcoinNetwork::class.java).toInstance(BitcoinNetwork.TESTNET)
  }

  @Provides
  fun provideResponseOperationTransactor(
    fakeResponseOperations: FakeResponseOperations
  ): Transactor<ResponseOperations> = FakeTransactor(fakeResponseOperations)

  @Provides
  fun provideWithdrawalOperationTransactor(
    fakeWithdrawalOperations: FakeWithdrawalOperations
  ): Transactor<WithdrawalOperations> = FakeTransactor(fakeWithdrawalOperations)
}
