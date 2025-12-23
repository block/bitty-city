package xyz.block.bittycity.outie.testing

import com.google.inject.Provides
import com.google.inject.Scopes
import com.squareup.moshi.Moshi
import jakarta.inject.Named
import org.bitcoinj.base.BitcoinNetwork
import java.time.Clock
import org.jooq.DSLContext
import xyz.block.bittycity.outie.OutieModule
import xyz.block.bittycity.outie.jooq.JooqResponseOperations
import xyz.block.bittycity.outie.jooq.JooqTransactor
import xyz.block.bittycity.outie.jooq.JooqWithdrawalOperations
import xyz.block.bittycity.outie.store.ResponseOperations
import xyz.block.bittycity.outie.store.TestPersistenceModule.Companion.DATASOURCE
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.outie.store.WithdrawalOperations
import xyz.block.bittycity.common.utils.WalletAddressParser

object BittyCityTestModule : OutieModule() {

  override fun installValidationModule() {
    bind(WalletAddressParser::class.java).`in`(Scopes.SINGLETON)
    bind(BitcoinNetwork::class.java).toInstance(BitcoinNetwork.TESTNET)
  }

  @Provides
  fun provideResponseOperationTransactor(
    @Named(DATASOURCE) dslContext: DSLContext,
    moshi: Moshi,
  ): Transactor<ResponseOperations> = JooqTransactor(dslContext) {
    JooqResponseOperations(it, moshi)
  }

  @Provides
  fun provideWithdrawalOperationTransactor(
    @Named(DATASOURCE) dslContext: DSLContext,
    moshi: Moshi,
    walletAddressParser: WalletAddressParser,
    clock: Clock,
  ): Transactor<WithdrawalOperations> = JooqTransactor(dslContext) {
    JooqWithdrawalOperations(it, walletAddressParser, clock, moshi)
  }
}
