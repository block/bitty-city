package xyz.block.bittycity.outie.client

import xyz.block.bittycity.outie.client.WithdrawRequest.Companion.toWithdrawalRequest
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.common.models.FlatFee
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.models.WithdrawalSpeedOption
import xyz.block.bittycity.outie.testing.Arbitrary
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import org.bitcoinj.base.Address
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import org.bitcoinj.base.AddressParser
import org.bitcoinj.base.BitcoinNetwork

class WithdrawRequestTest {
  private val validMetadata = emptyMap<String, String>()

  @Test
  fun `valid request should succeed`() {
    WithdrawRequest(
      withdrawalToken = Arbitrary.withdrawalToken.next(),
      customerId = Arbitrary.customerToken.next(),
      destinationAddress = Arbitrary.walletAddress.next(),
      amount = Arbitrary.amount.next(),
      fee = Arbitrary.fee.next(),
      speed = Arbitrary.speed.next(),
      metadata = validMetadata
    )
  }

  @Test
  fun `empty address should fail`() {
    shouldThrow<IllegalArgumentException> {
      WithdrawRequest(
        withdrawalToken = Arbitrary.withdrawalToken.next(),
        customerId = Arbitrary.customerToken.next(),
        destinationAddress = AddressParser.getDefault(BitcoinNetwork.TESTNET).parseAddress(""),
        amount = Arbitrary.amount.next(),
        fee = Arbitrary.fee.next(),
        speed = Arbitrary.speed.next(),
        metadata = validMetadata
      )
    }
  }

  @Test
  fun `zero amount should fail`() {
    shouldThrow<IllegalArgumentException> {
      WithdrawRequest(
        withdrawalToken = Arbitrary.withdrawalToken.next(),
        customerId = Arbitrary.customerToken.next(),
        destinationAddress = Arbitrary.walletAddress.next(),
        amount = Bitcoins(0),
        fee = Arbitrary.fee.next(),
        speed = Arbitrary.speed.next(),
        metadata = validMetadata
      )
    }.message shouldBe "Amount must be greater than zero"
  }

  @Test
  fun `negative fee should fail`() {
    shouldThrow<IllegalArgumentException> {
      WithdrawRequest(
        withdrawalToken = Arbitrary.withdrawalToken.next(),
        customerId = Arbitrary.customerToken.next(),
        destinationAddress = Arbitrary.walletAddress.next(),
        amount = Arbitrary.amount.next(),
        fee = Bitcoins(-1),
        speed = Arbitrary.speed.next(),
        metadata = validMetadata
      )
    }.message shouldBe "Fee must be non-negative"
  }

  private fun createWithdrawal(
    targetWalletAddress: Address? = Arbitrary.walletAddress.next(),
    amount: Bitcoins? = Arbitrary.amount.next(),
    fee: Bitcoins = Arbitrary.fee.next(),
    speed: WithdrawalSpeed = Arbitrary.speed.next()
  ) = Withdrawal(
    id = Arbitrary.withdrawalToken.next(),
    createdAt = Instant.now(),
    updatedAt = Instant.now(),
    version = 1L,
    customerId = Arbitrary.customerToken.next(),
    amount = amount,
    state = CollectingInfo,
    sourceBalanceToken = Arbitrary.balanceId.next(),
    targetWalletAddress = targetWalletAddress,
    selectedSpeed = WithdrawalSpeedOption(
      id = 1,
      speed = speed,
      totalFee = fee,
      totalFeeFiatEquivalent = Money.zero(CurrencyUnit.USD),
      serviceFee = FlatFee(Bitcoins(0)),
      approximateWaitTime = 1.minutes
    ),
    exchangeRate = Arbitrary.exchangeRate.next()
  )

  @Test
  fun `valid withdrawal converts to request successfully`() {
    val withdrawal = createWithdrawal()
    val request = withdrawal.toWithdrawalRequest().shouldBeSuccess()

    request.withdrawalToken shouldBe withdrawal.id
    request.customerId shouldBe withdrawal.customerId
    request.destinationAddress shouldBe withdrawal.targetWalletAddress
    request.amount shouldBe withdrawal.amount
    request.metadata shouldBe emptyMap()
  }

  @Test
  fun `missing target wallet address fails`() {
    val withdrawal = createWithdrawal(targetWalletAddress = null)
    withdrawal.toWithdrawalRequest()
      .shouldBeFailure()
      .message shouldBe "Target wallet address is required"
  }

  @Test
  fun `missing amount fails`() {
    val withdrawal = createWithdrawal(amount = null)
    withdrawal.toWithdrawalRequest()
      .shouldBeFailure()
      .message shouldBe "Amount is required"
  }
}
