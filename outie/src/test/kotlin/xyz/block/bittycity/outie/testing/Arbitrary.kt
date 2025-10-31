package xyz.block.bittycity.outie.testing

import xyz.block.bittycity.outie.client.WithdrawRequest
import xyz.block.bittycity.outie.models.BalanceId
import xyz.block.bittycity.outie.models.Bitcoins
import xyz.block.bittycity.outie.models.CustomerId
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalState.Companion.allStates
import xyz.block.bittycity.outie.models.WithdrawalToken
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.nonNegativeInt
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.stringPattern
import org.bitcoinj.base.Address
import org.bitcoinj.base.BitcoinNetwork
import org.bitcoinj.base.ScriptType
import org.bitcoinj.params.TestNet3Params
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import java.time.Instant
import java.util.UUID
import org.bitcoinj.base.SegwitAddress
import org.bitcoinj.crypto.ECKey

object Arbitrary {
  val uuid: Arb<UUID> = arbitrary { UUID.randomUUID() }
  val withdrawalToken: Arb<WithdrawalToken> = uuid.map { WithdrawalToken(it) }
  val customerToken: Arb<CustomerId> =
    Arb.stringPattern("[a-z0-9]{12}").map { CustomerId(it) }
  val stringToken: Arb<String> = Arb.stringPattern("[a-z0-9]{12}")
  val walletAddress: Arb<Address> = arbitrary {
    ECKey().toAddress(ScriptType.P2WPKH, BitcoinNetwork.TESTNET)
  }
  val balanceId: Arb<BalanceId> = uuid.map { BalanceId(it.toString()) }
  val speedOptionId = Arb.long(0, Long.MAX_VALUE)

  val usdEquivalent: Arb<Money> = arbitrary {
    Money.ofMinor(CurrencyUnit.USD, Arb.long(1L..10_000L).next())
  }

  val exchangeRate: Arb<Money> = arbitrary {
    Money.ofMinor(CurrencyUnit.USD, Arb.long(100_000_00L..120_000_00L).next())
  }

  // Generate satoshi values between 0.0001 BTC (10,000 satoshis) and 0.1 BTC (10,000,000 satoshis)
  val bitcoins: Arb<Bitcoins> = Arb.long(1L..10_000_000L).map { Bitcoins(it) }
  val fee: Arb<Bitcoins> = Arb.long(0L..1_000L).map { Bitcoins(it) }
  val amount: Arb<Bitcoins> = Arb.long(5_000L..10_000_000L).map { Bitcoins(it) }
  val speed: Arb<WithdrawalSpeed> = Arb.of(
    listOf(
      WithdrawalSpeed.RUSH,
      WithdrawalSpeed.PRIORITY,
      WithdrawalSpeed.STANDARD
    )
  )

  val outputIndex = Arb.int(0, Int.MAX_VALUE)

  val withdrawRequest: Arb<WithdrawRequest> = arbitrary {
    WithdrawRequest(
      withdrawalToken = withdrawalToken.bind(),
      customerId = customerToken.bind(),
      destinationAddress = walletAddress.bind(),
      amount = bitcoins.bind(),
      fee = bitcoins.bind(),
      speed = speed.bind(),
      metadata = emptyMap()
    )
  }

  val customerId: Arb<CustomerId> = arbitrary {
    CustomerId(stringToken.next())
  }

  val withdrawalState: Arb<WithdrawalState> = Arb.element(allStates)

  val failureReason: Arb<FailureReason> = Arb.enum<FailureReason>()

  val withdrawal: Arb<Withdrawal> = arbitrary {
    val createdInstant = Instant.now()

    Withdrawal(
      id = withdrawalToken.bind(),
      customerId = customerToken.bind(),
      sourceBalanceToken = balanceId.bind(),
      state = withdrawalState.bind(),
      source = stringToken.bind(),
      targetWalletAddress = walletAddress.bind(),
      amount = bitcoins.bind(),
      createdAt = createdInstant,
      updatedAt = createdInstant,
      failureReason = failureReason.orNull().bind(),
      version = Arb.nonNegativeInt().bind().toLong(),
      exchangeRate = exchangeRate.bind(),
    )
  }

  val testRunData: Arb<TestRunData> = arbitrary {
    val speed = speed.bind()

    TestRunData(
      withdrawalToken = withdrawalToken.bind(),
      customerToken = customerToken.bind(),
      balanceId = BalanceId(stringToken.bind()),
      targetWalletAddress = walletAddress.bind(),
      newWalletAddress = walletAddress.bind(),
      speed = speed,
      fee = bitcoins.bind(),
      bitcoins = bitcoins
        .filter { it >= Bitcoins(5000) }.bind(),
      exchangeRate = exchangeRate.bind(),
    )
  }

  val validTestnetAddressArb = arbitrary {
    ECKey().toAddress(ScriptType.P2WPKH, BitcoinNetwork.TESTNET)
  }
}
