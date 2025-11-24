package xyz.block.bittycity.innie.testing

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.stringPattern
import org.bitcoinj.base.Address
import org.bitcoinj.base.BitcoinNetwork
import org.bitcoinj.base.ScriptType
import org.bitcoinj.crypto.ECKey
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.innie.models.CheckingDepositRisk.allStates
import xyz.block.bittycity.innie.models.DepositFailureReason
import xyz.block.bittycity.innie.models.DepositReversalFailureReason
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import java.util.UUID

object Arbitrary {
  val uuid: Arb<UUID> = arbitrary { UUID.randomUUID() }
  val customerToken: Arb<CustomerId> =
    Arb.stringPattern("[a-z0-9]{12}").map { CustomerId(it) }
  val stringToken: Arb<String> = Arb.stringPattern("[a-z0-9]{12}")
  val depositToken: Arb<DepositToken> = stringToken.map { DepositToken(it) }
  val walletAddress: Arb<Address> = arbitrary {
    ECKey().toAddress(ScriptType.P2WPKH, BitcoinNetwork.TESTNET)
  }
  val exchangeRate: Arb<Money> = arbitrary {
    Money.ofMinor(CurrencyUnit.USD, Arb.long(100_000_00L..120_000_00L).next())
  }
  val bitcoins: Arb<Bitcoins> = Arb.long(1L..10_000_000L).map { Bitcoins(it) }
  val amount: Arb<Bitcoins> = Arb.long(5_000L..10_000_000L).map { Bitcoins(it) }
  val outputIndex = Arb.int(0, Int.MAX_VALUE)
  val customerId: Arb<CustomerId> = arbitrary {
    CustomerId(stringToken.next())
  }
  val depositState: Arb<DepositState> = Arb.element(allStates)
  val depositFailureReason: Arb<DepositFailureReason> = Arb.enum<DepositFailureReason>()
  val depositReversalFailureReason: Arb<DepositReversalFailureReason> = Arb.enum<DepositReversalFailureReason>()


  val testRunData: Arb<TestRunData> = arbitrary {
    TestRunData(
      depositToken = depositToken.bind(),
      customerToken = customerToken.bind(),
      bitcoins = amount.bind(),
      exchangeRate = exchangeRate.bind(),
      targetWalletAddress = walletAddress.bind(),
      blockchainTransactionId = stringToken.bind(),
      blockchainTransactionOutputIndex = outputIndex.bind(),
      paymentToken = stringToken.bind()
    )
  }
}
