package xyz.block.bittycity.outie.json

import xyz.block.bittycity.common.models.BitcoinDisplayUnits
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.utils.CurrencyConversionUtils
import xyz.block.bittycity.outie.models.CurrencyDisplayPreference
import xyz.block.bittycity.outie.models.CurrencyDisplayUnits
import xyz.block.bittycity.outie.models.FlatFee
import xyz.block.bittycity.outie.models.Inputs
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdle.MaximumAmountReason.BALANCE
import xyz.block.bittycity.outie.models.WithdrawalHurdleResponse.AmountHurdleResponse
import xyz.block.bittycity.outie.models.WithdrawalHurdleResponse.SpeedHurdleResponse
import xyz.block.bittycity.outie.models.WithdrawalHurdleResponse.TargetWalletAddressHurdleResponse
import xyz.block.bittycity.outie.models.WithdrawalNotification
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.models.WithdrawalSpeedOption
import xyz.block.bittycity.outie.models.WithdrawalToken
import com.squareup.moshi.Types
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import org.junit.jupiter.api.Test
import xyz.block.domainapi.ResultCode.CLEARED
import xyz.block.domainapi.UserInteraction
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import org.bitcoinj.base.AddressParser
import org.bitcoinj.base.BitcoinNetwork

class WithdrawalMoshiTest {

  @Test
  fun `inputs are serialised and deserialised correctly`() {
    val moshi = WithdrawalMoshi.create()

    val hurdleResponses = listOf(
      TargetWalletAddressHurdleResponse(CLEARED, "mkHS9ne12qx9pS9VojpwU5xtRd4T7X7ZUt"),
      AmountHurdleResponse(CLEARED, Bitcoins(1000)),
      SpeedHurdleResponse(CLEARED, WithdrawalSpeed.RUSH)
    )

    val id = WithdrawalToken.parse(UUID.randomUUID().toString()).getOrThrow()
    val inputs = Inputs(id, 1, hurdleResponses)
    val inputsAdapter = moshi.adapter(Inputs::class.java)
    val serialised = inputsAdapter.toJson(inputs)
    val deserialised = inputsAdapter.fromJson(serialised)

    deserialised.shouldNotBeNull()
    deserialised.id shouldBe id
    deserialised.backCounter shouldBe 1
    deserialised.hurdleResponses.size shouldBe 3
    deserialised.hurdleResponses[0] shouldBe hurdleResponses[0]
    deserialised.hurdleResponses[1] shouldBe hurdleResponses[1]
    deserialised.hurdleResponses[2] shouldBe hurdleResponses[2]
  }

  @Test
  fun `user interactions are serialised and deserialised correctly`() {
    val moshi = WithdrawalMoshi.create()

    val userInteractions = listOf(
      WithdrawalHurdle.TargetWalletAddressHurdle(),
      WithdrawalHurdle.AmountHurdle(
        Bitcoins(10000),
        displayUnits = CurrencyDisplayPreference(
          CurrencyDisplayUnits.BITCOIN,
          BitcoinDisplayUnits.SATOSHIS
        ),
        targetWalletAddress = AddressParser.getDefault(BitcoinNetwork.TESTNET).parseAddress(
          "mkHS9ne12qx9pS9VojpwU5xtRd4T7X7ZUt"
        ),
        minimumAmount = Bitcoins(5000),
        maximumAmount = Bitcoins(10000),
        maximumAmountReason = BALANCE,
        exchangeRate = Money.ofMinor(CurrencyUnit.USD, 1000),
        maximumAmountFiatEquivalent = CurrencyConversionUtils.bitcoinsToUsd(
          Bitcoins(10000),
          Money.ofMinor(CurrencyUnit.USD, 1000)
        )
      ),
      WithdrawalHurdle.SpeedHurdle(
        withdrawalSpeedOptions = listOf(
          WithdrawalSpeedOption(
            speed = WithdrawalSpeed.PRIORITY,
            totalFee = Bitcoins(3000L),
            totalFeeFiatEquivalent = Money.ofMinor(CurrencyUnit.USD, 300),
            serviceFee = FlatFee(Bitcoins(0L)),
            approximateWaitTime = 1.minutes
          ),
          WithdrawalSpeedOption(
            speed = WithdrawalSpeed.RUSH,
            totalFee = Bitcoins(2000L),
            totalFeeFiatEquivalent = Money.ofMinor(CurrencyUnit.USD, 200),
            serviceFee = FlatFee(Bitcoins(0L)),
            approximateWaitTime = 10.minutes
          )
        ),
        currentBalance = Bitcoins(50_000L),
        freeTierMinAmount = Bitcoins(100_000L),
        displayUnits = BitcoinDisplayUnits.SATOSHIS,
      ),
      WithdrawalHurdle.SelfAttestationHurdle,
      WithdrawalNotification.WithdrawalCancelledNotification
    )

    val type = Types.newParameterizedType(
      UserInteraction::class.java,
      RequirementId::class.java
    )

    val userInteractionsAdapter = moshi.adapter<UserInteraction<RequirementId>>(type)
    val serialised = userInteractions.map { userInteractionsAdapter.toJson(it) }
    val deserialised = serialised.map { userInteractionsAdapter.fromJson(it) }

    deserialised[0] shouldBe userInteractions[0]
    deserialised[1] shouldBe userInteractions[1]
    deserialised[2] shouldBe userInteractions[2]
    deserialised[3] shouldBe userInteractions[3]
    deserialised[4] shouldBe userInteractions[4]
  }

  @Test
  fun `JSON serialisation supports old serialisation format`() {
    val moshi = WithdrawalMoshi.create()

    val json = "{\"" +
      "id\": \"BTCW_f3382df0-55a9-356a-8967-a0664dd667ea\", " +
      "\"note\": \"super_test\", " +
      "\"state\": \"CHECKING_RISK\", " +
      "\"amount\": 5008, " +
      "\"source\": \"BITTY\", " +
      "\"version\": 2, " +
      "\"createdAt\": 1750102630000, " +
      "\"updatedAt\": 1750102754000, " +
      "\"customerId\": \"MLKNP0QR8YSAK\", " +
      "\"feeRefunded\": false, " +
      "\"selectedSpeed\": {" +
      "\"id\": 41, " +
      "\"speed\": \"PRIORITY\", " +
      "\"totalFee\": 3023, " +
      "\"selectable\": false, " +
      "\"serviceFee\": {\"type\": \"FlatFee\", \"value\": 2760}, " +
      "\"approximateWaitTime\": \"2h\", " +
      "\"totalFeeFiatEquivalent\": {\"cents\": 30, \"currency\": \"USD\"}}, " +
      "\"userHasConfirmed\": true, " +
      "\"sourceBalanceToken\": \"pbact_0bwdxeH7bSh2hDNqobAwwQQ\", " +
      "\"targetWalletAddress\": \"tb1qs3a2wcugr2ulnzg03rrt996a9lq6ryg3h6ysjm\", " +
      "\"fiatEquivalentAmount\": {\"cents\": 5008, \"currency\": \"USD\"}}"

    val withdrawalAdapter = moshi.adapter(Withdrawal::class.java)
    val deserialised = withdrawalAdapter.fromJson(json)

    // Exchange rate was not saved before
    deserialised?.exchangeRate.shouldBeNull()
    // Fiat equivalent amount used to be stored but now is derived from the exchange rate
    deserialised?.fiatEquivalentAmount.shouldBeNull()
  }
}
