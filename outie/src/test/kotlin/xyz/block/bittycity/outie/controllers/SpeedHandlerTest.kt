package xyz.block.bittycity.outie.controllers

import app.cash.quiver.extensions.success
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import jakarta.inject.Inject
import kotlin.time.Duration.Companion.minutes
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import org.junit.jupiter.api.Test
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.common.models.FlatFee
import xyz.block.bittycity.outie.models.OnchainFeeQuote
import xyz.block.bittycity.common.utils.CurrencyConversionUtils.bitcoinsToUsd
import xyz.block.bittycity.outie.models.WithdrawalHurdle
import xyz.block.bittycity.outie.models.WithdrawalServiceFeeQuote
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.models.WithdrawalSpeedOption.Companion.BLOCK_TARGET_12
import xyz.block.bittycity.outie.models.WithdrawalSpeedOption.Companion.BLOCK_TARGET_144
import xyz.block.bittycity.outie.models.WithdrawalSpeedOption.Companion.BLOCK_TARGET_2
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import xyz.block.bittycity.outie.validation.InsufficientBalance
import xyz.block.bittycity.outie.validation.ParameterIsRequired

class SpeedHandlerTest : BittyCityTestCase() {

  @Inject lateinit var subject: SpeedHandler

  @Test
  fun `getHurdle returns SpeedHurdle with correct speed options when exchange rate is present`() =
    runTest {
      val amount = Bitcoins(5000L)
      val withdrawal = data.seedWithdrawal(
        state = CollectingInfo,
        amount = amount,
        walletAddress = data.targetWalletAddress
      )
      val currentBalance = Bitcoins(15000L)
      setupFakes(currentBalance)

      val result = subject.getHurdles(withdrawal, currentBalance).getOrThrow()
        .first() as WithdrawalHurdle.SpeedHurdle

      assertSoftly {
        result.shouldBeInstanceOf<WithdrawalHurdle.SpeedHurdle>()
        result.currentBalance shouldBe currentBalance
        result.freeTierMinAmount shouldBe Bitcoins(minAmountFreeTier)
        result.withdrawalSpeedOptions should haveSize(3)
        val options = result.withdrawalSpeedOptions.associateBy { it.speed }

        options[WithdrawalSpeed.PRIORITY].shouldNotBeNull() should {
          it.approximateWaitTime shouldBe (BLOCK_TARGET_2 * 10L).minutes
          it.selectable shouldBe true
          it.serviceFee shouldBe FlatFee(Bitcoins(1_000))
          it.totalFee shouldBe Bitcoins(3_000)
          it.totalFeeFiatEquivalent shouldBe bitcoinsToUsd(
            Bitcoins(3_000),
            withdrawal.exchangeRate!!
          )
        }

        options[WithdrawalSpeed.RUSH].shouldNotBeNull() should {
          it.approximateWaitTime shouldBe (BLOCK_TARGET_12 * 10L).minutes
          it.selectable shouldBe true
          it.serviceFee shouldBe FlatFee(Bitcoins(500))
          it.totalFee shouldBe Bitcoins(2_000)
          it.totalFeeFiatEquivalent shouldBe bitcoinsToUsd(
            Bitcoins(2_000),
            withdrawal.exchangeRate!!
          )
        }

        options[WithdrawalSpeed.STANDARD].shouldNotBeNull() should {
          it.approximateWaitTime shouldBe (BLOCK_TARGET_144 * 10L).minutes
          it.selectable shouldBe false
          it.serviceFee shouldBe FlatFee(Bitcoins(0))
          it.totalFee shouldBe Bitcoins(0)
          it.totalFeeFiatEquivalent shouldBe Money.zero(CurrencyUnit.USD)
        }
      }
    }

  @Test
  fun `getHurdle fails when exchange rate is missing`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CollectingInfo
    ).copy(exchangeRate = null)
    val currentBalance = Bitcoins(10000L)

    setupFakes(currentBalance)

    val result = subject.getHurdles(withdrawal, currentBalance)

    result.shouldBeFailure()
    result.shouldBeFailure<ParameterIsRequired>()
  }

  @Test
  fun `standard speed is enabled when amount is sufficient`() = runTest {
    val amount = Bitcoins(100_000L)
    val withdrawal = data.seedWithdrawal(
      state = CollectingInfo,
      amount = amount,
      walletAddress = data.targetWalletAddress
    )
    val currentBalance = Bitcoins(100_000L)

    setupFakes(currentBalance)

    (
      subject.getHurdles(withdrawal, currentBalance).getOrThrow()
        .first() as WithdrawalHurdle.SpeedHurdle
      )
      .withdrawalSpeedOptions.find { it.speed == WithdrawalSpeed.STANDARD }.shouldNotBeNull()
      .selectable shouldBe true
  }

  @Test
  fun `speeds are enabled and adjusted amounts populated when amount is insufficient to cover fees`() =
    runTest {
      val amount = Bitcoins(120_000L)
      val withdrawal = data.seedWithdrawal(
        state = CollectingInfo,
        amount = amount,
        walletAddress = data.targetWalletAddress
      )
      val currentBalance = Bitcoins(120_100L)

      setupFakes(currentBalance)

      val speedHurdle = (
        subject.getHurdles(withdrawal, currentBalance).getOrThrow()
          .first() as WithdrawalHurdle.SpeedHurdle
        )

      speedHurdle.withdrawalSpeedOptions.size shouldBe 3
      speedHurdle.withdrawalSpeedOptions[0].should { priority ->
        priority.selectable shouldBe true
        priority.adjustedAmount shouldBe Bitcoins(117_100L)
      }
      speedHurdle.withdrawalSpeedOptions[1].should { rush ->
        rush.selectable shouldBe true
        rush.adjustedAmount shouldBe Bitcoins(118_100L)
      }
      speedHurdle.withdrawalSpeedOptions[2].should { standard ->
        standard.selectable shouldBe true
        standard.adjustedAmount.shouldBeNull()
      }
    }

  @Test
  fun `only rush is available, with an adjustment`() = runTest {
    val amount = Bitcoins(7_000L)
    val withdrawal = data.seedWithdrawal(
      state = CollectingInfo,
      amount = amount,
      walletAddress = data.targetWalletAddress
    )
    val currentBalance = Bitcoins(7_000L)

    setupFakes(currentBalance)

    val speedHurdle = (
      subject.getHurdles(withdrawal, currentBalance).getOrThrow()
        .first() as WithdrawalHurdle.SpeedHurdle
      )

    speedHurdle.withdrawalSpeedOptions.size shouldBe 3
    speedHurdle.withdrawalSpeedOptions[0].should { priority ->
      priority.selectable shouldBe false
      priority.adjustedAmount.shouldBeNull()
    }
    speedHurdle.withdrawalSpeedOptions[1].should { rush ->
      rush.selectable shouldBe true
      rush.adjustedAmount shouldBe Bitcoins(5000L)
    }
    speedHurdle.withdrawalSpeedOptions[2].should { standard ->
      standard.selectable shouldBe false
      standard.adjustedAmount.shouldBeNull()
    }
  }

  @Test
  fun `rush is selectable with reduced fee when balance barely covers minimum`() = runTest {
    val amount = Bitcoins(6_999L)
    val withdrawal = data.seedWithdrawal(
      state = CollectingInfo,
      walletAddress = data.targetWalletAddress,
      amount = amount
    )
    val currentBalance = Bitcoins(6_999L)

    setupFakes(currentBalance)

    val speedHurdle = (
      subject.getHurdles(withdrawal, currentBalance).getOrThrow()
        .first() as WithdrawalHurdle.SpeedHurdle
      )

    speedHurdle.withdrawalSpeedOptions.size shouldBe 3
    speedHurdle.withdrawalSpeedOptions[0].should { priority ->
      priority.selectable shouldBe false
      priority.adjustedAmount.shouldBeNull()
    }
    speedHurdle.withdrawalSpeedOptions[1].should { rush ->
      rush.selectable shouldBe true
      rush.maximumAmount shouldBe Bitcoins(5_000L)
      rush.adjustedAmount shouldBe Bitcoins(5_000L)
    }
    speedHurdle.withdrawalSpeedOptions[2].should { standard ->
      standard.selectable shouldBe false
      standard.adjustedAmount.shouldBeNull()
    }
  }

  @Test
  fun `it's impossible to withdraw a balance that is too low`() = runTest {
    val amount = Bitcoins(5_000L)
    val withdrawal = data.seedWithdrawal(
      state = CollectingInfo,
      walletAddress = data.targetWalletAddress,
      amount = amount
    )
    val currentBalance = Bitcoins(5_000L)

    setupFakes(currentBalance)

    shouldThrow<InsufficientBalance> {
      (
        subject.getHurdles(withdrawal, currentBalance).getOrThrow()
          .first() as WithdrawalHurdle.SpeedHurdle
        )
    }
  }

  private fun setupFakes(balance: Bitcoins) {
    setup { app ->
      app.feeQuoteClient.nextOnchainFeeQuote = Result.success(
        listOf(
          OnchainFeeQuote(
            fee = Bitcoins(2000L),
            blockTarget = BLOCK_TARGET_2
          ),
          OnchainFeeQuote(
            fee = Bitcoins(1500L),
            blockTarget = BLOCK_TARGET_12
          ),
          OnchainFeeQuote(
            fee = Bitcoins(0L),
            blockTarget = BLOCK_TARGET_144
          )
        )
      )

      app.feeQuoteClient.nextWithdrawalServiceFeeQuote = Result.success(
        listOf(
          WithdrawalServiceFeeQuote(
            speed = WithdrawalSpeed.PRIORITY,
            fee = FlatFee(Bitcoins(1000L))
          ),
          WithdrawalServiceFeeQuote(
            speed = WithdrawalSpeed.RUSH,
            fee = FlatFee(Bitcoins(500L))
          ),
          WithdrawalServiceFeeQuote(
            speed = WithdrawalSpeed.STANDARD,
            fee = FlatFee(Bitcoins(0L))
          )
        )
      )

      app.ledgerClient.nextBalance = balance.success()
    }
  }
}
