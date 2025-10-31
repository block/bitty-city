package xyz.block.bittycity.outie.controllers

import app.cash.quiver.extensions.success
import xyz.block.bittycity.outie.models.BalanceId
import xyz.block.bittycity.outie.models.BitcoinAccount
import xyz.block.bittycity.outie.models.BitcoinDisplayUnits
import xyz.block.bittycity.outie.models.BitcoinDisplayUnits.BITCOIN
import xyz.block.bittycity.outie.models.Bitcoins
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.outie.models.CurrencyDisplayPreference
import xyz.block.bittycity.outie.models.CurrencyDisplayUnits.USD
import xyz.block.bittycity.outie.models.FlatFee
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdle.MaximumAmountReason.BALANCE
import xyz.block.bittycity.outie.models.WithdrawalHurdleResponse
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.models.WithdrawalSpeed.PRIORITY
import xyz.block.bittycity.outie.models.WithdrawalSpeed.RUSH
import xyz.block.bittycity.outie.models.WithdrawalSpeed.STANDARD
import xyz.block.bittycity.outie.models.WithdrawalSpeedOption
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.Arbitrary.speedOptionId
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import xyz.block.bittycity.outie.testing.TestApp
import xyz.block.bittycity.outie.testing.shouldBeSpeedHurdle
import xyz.block.bittycity.outie.validation.InsufficientBalance
import xyz.block.bittycity.outie.validation.InvalidNoteError
import xyz.block.bittycity.outie.validation.ValidationService.Companion.MAX_NOTE_LENGTH
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import org.junit.jupiter.api.Test
import xyz.block.domainapi.DomainApi
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.ResultCode
import xyz.block.domainapi.util.Operation
import kotlin.time.Duration.Companion.minutes

class InfoCollectionControllerTest : BittyCityTestCase() {

  @Inject lateinit var subject: InfoCollectionController

  @Test
  fun `returns all hurdles when no info has been collected`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CollectingInfo
    )
    val balance = Bitcoins(50000L)
    setupFakes(this, withdrawal, balance, withdrawal.sourceBalanceToken)

    val result = subject.processInputs(
      withdrawal,
      emptyList(),
      Operation.EXECUTE
    ).getOrThrow()
    val exchangeRate = data.exchangeRate
    result.shouldBeInstanceOf<ProcessingState.UserInteractions<*, *>>()
    result.hurdles.size shouldBe 3
    result.hurdles[0] shouldBe WithdrawalHurdle.TargetWalletAddressHurdle()
    result.hurdles[1] shouldBe WithdrawalHurdle.AmountHurdle(
      currentBalance = balance,
      displayUnits = CurrencyDisplayPreference(USD, BITCOIN),
      targetWalletAddress = withdrawal.targetWalletAddress,
      minimumAmount = Bitcoins(5000L),
      maximumAmount = Bitcoins(50000L),
      maximumAmountReason = BALANCE,
      exchangeRate = exchangeRate,
      maximumAmountFiatEquivalent = Withdrawal.satoshiToUsd(Bitcoins(50000L), exchangeRate)
    )
    result.hurdles[2] shouldBe WithdrawalHurdle.NoteHurdle(MAX_NOTE_LENGTH)
  }

  @Test
  fun `returns remaining hurdles when only amount has been collected`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CollectingInfo,
      walletAddress = Arbitrary.walletAddress.next(),
      amount = Bitcoins(7999L)
    )
    val balance = Bitcoins(10000L)
    setupFakes(this, withdrawal, balance, withdrawal.sourceBalanceToken)

    val expectedSpeedHurdle = getExpectedSpeedHurdle(
      options = listOf(
        createSpeedOption(
          PRIORITY,
          Bitcoins(3000L),
          Bitcoins(0L),
          Bitcoins(minAmount),
          balance - Bitcoins(3000L),
          selectable = true,
          adjustedAmount = balance - Bitcoins(3000L),
          withdrawal.exchangeRate!!
        ),
        createSpeedOption(
          RUSH,
          Bitcoins(2000L),
          Bitcoins(0L),
          Bitcoins(minAmount),
          balance - Bitcoins(2000L),
          selectable = true,
          adjustedAmount = null,
          withdrawal.exchangeRate as Money
        ),
        createSpeedOption(
          STANDARD,
          Bitcoins(0L),
          selectable = false,
          exchangeRate = withdrawal.exchangeRate as Money
        )
      ),
      currentBalance = balance,
      freeTierMinAmount = Bitcoins(minAmountFreeTier),
      displayUnits = BitcoinDisplayUnits.SATOSHIS
    )

    val result = subject.processInputs(
      withdrawal,
      emptyList(),
      Operation.EXECUTE
    ).getOrThrow()
    result.shouldBeInstanceOf<ProcessingState.UserInteractions<*, *>>()
    result.hurdles.size shouldBe 3
    result.hurdles[0] shouldBe WithdrawalHurdle.NoteHurdle(MAX_NOTE_LENGTH)
    result.hurdles[1].shouldBeSpeedHurdle(expectedSpeedHurdle)
    val confirmationHurdle = result.hurdles[2]
    confirmationHurdle.shouldBeInstanceOf<WithdrawalHurdle.ConfirmationHurdle>()
    confirmationHurdle.selectedSpeed.shouldBeNull()
    confirmationHurdle.amount shouldBe withdrawal.amount
    confirmationHurdle.walletAddress shouldBe withdrawal.targetWalletAddress
    result.nextEndpoint shouldBe DomainApi.Endpoint.SECURE_EXECUTE
  }

  @Test
  fun `does not fail if balance is below minimum amount`() = runTest {
    val withdrawal = data.seedWithdrawal(state = CollectingInfo)
    val balance = Bitcoins(2000L)
    val exchangeRate = data.exchangeRate
    setupFakes(this, withdrawal, balance, withdrawal.sourceBalanceToken)

    val result = subject.processInputs(
      withdrawal,
      emptyList(),
      Operation.EXECUTE
    ).getOrThrow()

    result.shouldBeInstanceOf<ProcessingState.UserInteractions<*, *>>()
    result.hurdles.size shouldBe 3
    result.hurdles[0] shouldBe WithdrawalHurdle.TargetWalletAddressHurdle()
    result.hurdles[1] shouldBe WithdrawalHurdle.AmountHurdle(
      currentBalance = balance,
      displayUnits = CurrencyDisplayPreference(USD, BITCOIN),
      targetWalletAddress = withdrawal.targetWalletAddress,
      minimumAmount = Bitcoins(5000L),
      maximumAmount = balance,
      maximumAmountReason = BALANCE,
      exchangeRate = exchangeRate,
      maximumAmountFiatEquivalent = Withdrawal.satoshiToUsd(balance, exchangeRate)
    )
    result.hurdles[2] shouldBe WithdrawalHurdle.NoteHurdle(MAX_NOTE_LENGTH)
  }

  @Test
  fun `fails if balance is below withdrawal amount`() = runTest {
    val withdrawal = data.seedWithdrawal(state = CollectingInfo)
    setupFakes(this, withdrawal, Bitcoins(10000L))

    subject.processInputs(
      withdrawal,
      listOf(
        WithdrawalHurdleResponse.AmountHurdleResponse(
          code = ResultCode.CLEARED,
          userAmount = Bitcoins(10001L)
        )
      ),
      Operation.EXECUTE
    ) shouldBeFailure (
      InsufficientBalance(
        currentBalance = Bitcoins(10000L),
        requiredMinimumBalance = Bitcoins(minAmount),
        availableBalanceAfterMinFee = Bitcoins(10000L),
        requestedAmount = Bitcoins(10001L)
      )
      )
  }

  @Test
  fun `fails if note is too long`() = runTest {
    val withdrawal = data.seedWithdrawal(state = CollectingInfo)
    setupFakes(this, withdrawal, Bitcoins(10000L))

    subject.processInputs(
      withdrawal,
      listOf(
        WithdrawalHurdleResponse.NoteHurdleResponse(
          code = ResultCode.CLEARED,
          note = "a".repeat(401)
        )
      ),
      Operation.EXECUTE
    ) shouldBeFailure (
      InvalidNoteError("note exceeds the maximum length of $MAX_NOTE_LENGTH characters")
      )
  }

  @Test
  fun `fails if note has control characters`() = runTest {
    val withdrawal = data.seedWithdrawal(state = CollectingInfo)
    setupFakes(this, withdrawal, Bitcoins(10000L))

    subject.processInputs(
      withdrawal,
      listOf(
        WithdrawalHurdleResponse.NoteHurdleResponse(
          code = ResultCode.CLEARED,
          note = "Hello\u0000World\u0007!"
        )
      ),
      Operation.EXECUTE
    ) shouldBeFailure (
      InvalidNoteError("note contains invalid control characters")
      )
  }

  @Test
  fun `successfully processes hurdle results`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CollectingInfo,
      walletAddress = Arbitrary.walletAddress.next(),
      amount = Bitcoins(11000L),
      note = ""
    )
    setupFakes(this, withdrawal, Bitcoins(20000L))

    val result = subject.processInputs(
      withdrawal,
      listOf(
        WithdrawalHurdleResponse.SpeedHurdleResponse(
          code = ResultCode.CLEARED,
          selectedSpeed = RUSH
        ),
        WithdrawalHurdleResponse.ConfirmationHurdleResponse(code = ResultCode.CLEARED),
      ),
      Operation.EXECUTE
    ).getOrThrow()

    result.shouldBeInstanceOf<ProcessingState.Complete<*, *>>()
    val updated = result.value as Withdrawal
    updated.amount shouldBe Bitcoins(11000L)
    updated.note shouldBe ""
    updated.selectedSpeed?.totalFee shouldBe Bitcoins(2000L)
    updated.selectedSpeed?.speed shouldBe RUSH
    updated.selectedSpeed?.serviceFee shouldBe FlatFee(Bitcoins(0L))
    updated.userHasConfirmed shouldBe true
  }

  @Test
  fun `cancelling withdrawal updates state and sets failure reason`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CollectingInfo,
      walletAddress = Arbitrary.walletAddress.next()
    )
    setupFakes(this, withdrawal, Bitcoins(20000L))

    shouldThrow<DomainApiError.ProcessWasCancelled> {
      subject.processInputs(
        withdrawal,
        listOf(
          WithdrawalHurdleResponse.AmountHurdleResponse(
            code = ResultCode.CLEARED,
            userAmount = Bitcoins(11000L),
          ),
          WithdrawalHurdleResponse.NoteHurdleResponse(code = ResultCode.SKIPPED),
          WithdrawalHurdleResponse.SpeedHurdleResponse(code = ResultCode.CANCELLED),
        ),
        Operation.EXECUTE
      ).getOrThrow()
    }
  }

  @Suppress("LongParameterList")
  private fun createSpeedOption(
    speed: WithdrawalSpeed,
    serviceFee: Bitcoins,
    onChainFee: Bitcoins = Bitcoins(0L),
    minimumAmount: Bitcoins? = null,
    maximumAmount: Bitcoins? = null,
    selectable: Boolean? = null,
    adjustedAmount: Bitcoins? = null,
    exchangeRate: Money,
  ) = WithdrawalSpeedOption(
    id = speedOptionId.next(),
    speed = speed,
    totalFee = onChainFee + serviceFee,
    totalFeeFiatEquivalent = Money.ofMinor(CurrencyUnit.USD, 0),
    serviceFee = FlatFee(serviceFee),
    approximateWaitTime = 1.minutes,
    minimumAmount = minimumAmount,
    maximumAmount = maximumAmount,
    selectable = selectable,
    adjustedAmount = adjustedAmount,
    adjustedAmountFiatEquivalent = adjustedAmount?.let { Withdrawal.satoshiToUsd(it, exchangeRate) }
  )

  private fun getExpectedSpeedHurdle(
    options: List<WithdrawalSpeedOption>,
    currentBalance: Bitcoins,
    freeTierMinAmount: Bitcoins,
    displayUnits: BitcoinDisplayUnits,
  ): WithdrawalHurdle.SpeedHurdle = WithdrawalHurdle.SpeedHurdle(
    withdrawalSpeedOptions = options,
    currentBalance = currentBalance,
    freeTierMinAmount = freeTierMinAmount,
    displayUnits = displayUnits
  )

  private fun setupFakes(
    testApp: TestApp,
    withdrawal: Withdrawal,
    balance: Bitcoins,
    sourceBalanceToken: BalanceId? = null,
  ) {
    testApp.bitcoinAccountService.nextBitcoinAccount = BitcoinAccount(
      customerId = withdrawal.customerId,
      balanceId = sourceBalanceToken ?: Arbitrary.balanceId.next(),
      currencyDisplayPreference = CurrencyDisplayPreference(USD, BITCOIN)
    )
    testApp.ledgerClient.nextBalance = balance.success()
  }
}
