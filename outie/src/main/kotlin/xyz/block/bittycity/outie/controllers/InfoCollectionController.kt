package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import xyz.block.bittycity.outie.client.BitcoinAccountClient
import xyz.block.bittycity.common.client.CurrencyDisplayPreferenceClient
import xyz.block.bittycity.outie.client.FeeQuoteClient
import xyz.block.bittycity.outie.client.LedgerClient
import xyz.block.bittycity.outie.client.LimitClient
import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.models.BalanceId
import xyz.block.bittycity.outie.models.BitcoinAccount
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.outie.models.CheckingSanctions
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.RequirementId.AMOUNT
import xyz.block.bittycity.outie.models.RequirementId.NOTE
import xyz.block.bittycity.outie.models.RequirementId.SPEED
import xyz.block.bittycity.outie.models.RequirementId.TARGET_WALLET_ADDRESS
import xyz.block.bittycity.outie.models.RequirementId.USER_CONFIRMATION
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.common.utils.CurrencyConversionUtils.bitcoinsToUsd
import xyz.block.bittycity.common.utils.CurrencyConversionUtils.usdToBitcoins
import xyz.block.bittycity.outie.models.WithdrawalHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdle.AmountHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdle.MaximumAmountReason.BALANCE
import xyz.block.bittycity.outie.models.WithdrawalHurdle.MaximumAmountReason.DAILY_LIMIT
import xyz.block.bittycity.outie.models.WithdrawalHurdle.MaximumAmountReason.WEEKLY_LIMIT
import xyz.block.bittycity.outie.models.WithdrawalHurdle.NoteHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdle.SpeedHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdle.TargetWalletAddressHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdleResponse
import xyz.block.bittycity.outie.models.WithdrawalLimitInfo
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.models.WithdrawalSpeed.PRIORITY
import xyz.block.bittycity.outie.models.WithdrawalSpeed.RUSH
import xyz.block.bittycity.outie.models.WithdrawalSpeed.STANDARD
import xyz.block.bittycity.outie.models.WithdrawalSpeedOption
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import xyz.block.bittycity.outie.validation.AmountBelowFreeTier
import xyz.block.bittycity.outie.validation.InsufficientBalance
import xyz.block.bittycity.outie.validation.InvalidBlockTarget
import xyz.block.bittycity.outie.validation.ParameterIsRequired
import xyz.block.bittycity.outie.validation.ValidationService
import xyz.block.bittycity.outie.validation.ValidationService.Companion.MAX_NOTE_LENGTH
import xyz.block.bittycity.outie.validation.WalletAddressParser
import jakarta.inject.Inject
import jakarta.inject.Named
import org.joda.money.Money
import xyz.block.domainapi.DomainApiError.UnsupportedHurdleResultCode
import xyz.block.domainapi.Input
import xyz.block.domainapi.ResultCode
import xyz.block.domainapi.UserInteraction.Hurdle
import xyz.block.domainapi.util.HurdleGroup
import java.math.BigDecimal
import kotlin.time.Duration.Companion.minutes

@Suppress("LongParameterList")
class InfoCollectionController @Inject constructor(
  stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState>,
  withdrawalStore: WithdrawalStore,
  private val targetWalletAddressHandler: TargetWalletAddressHandler,
  private val amountHandler: AmountHandler,
  private val speedHandler: SpeedHandler,
  private val noteHandler: NoteHandler,
  private val confirmationHandler: ConfirmationHandler,
  private val ledgerClient: LedgerClient,
  metricsClient: MetricsClient,
) : WithdrawalInfoCollectionController(
  pendingCollectionState = CollectingInfo,
  stateMachine = stateMachine,
  metricsClient = metricsClient,
  withdrawalStore = withdrawalStore,
) {

  override val hurdleGroups = mapOf(
    "mobile" to HurdleGroup(
      id = "mobile",
      groups = listOf(
        setOf(TARGET_WALLET_ADDRESS, AMOUNT, NOTE),
        setOf(SPEED),
        setOf(USER_CONFIRMATION),
      )
    )
  )

  override fun findMissingRequirements(value: Withdrawal): Result<List<RequirementId>> = result {
    listOf(
      TARGET_WALLET_ADDRESS to { (value.targetWalletAddress == null) },
      AMOUNT to { (value.amount?.units ?: 0L) == 0L },
      NOTE to { value.note == null },
      SPEED to
        {
          value.selectedSpeed == null && value.targetWalletAddress != null && value.amount != null
        },
      USER_CONFIRMATION to {
        value.userHasConfirmed == null && value.targetWalletAddress != null && value.amount != null
      },
    ).filter { (_, predicate) -> predicate() }
      .map { (key, _) -> key }
  }

  override fun getHurdlesForRequirementId(
    requirementId: RequirementId,
    value: Withdrawal,
    previousHurdles: List<Hurdle<RequirementId>>
  ): Result<List<Hurdle<RequirementId>>> = result {
    when (requirementId) {
      TARGET_WALLET_ADDRESS -> listOf(targetWalletAddressHandler.getHurdle(value))
      AMOUNT -> listOf(
        amountHandler.getHurdle(
          value,
          getBalance(value.customerId, value.sourceBalanceToken).bind()
        ).bind()
      )
      NOTE -> listOf(noteHandler.getHurdle(value))
      SPEED -> speedHandler.getHurdles(
        value,
        getBalance(value.customerId, value.sourceBalanceToken).bind()
      ).bind()
      USER_CONFIRMATION -> listOf(
        confirmationHandler.getHurdle(
          value,
          getBalance(value.customerId, value.sourceBalanceToken).bind()
        ).bind()
      )
      else -> emptyList()
    }
  }

  override fun updateValue(
    value: Withdrawal,
    hurdleResponse: Input.HurdleResponse<RequirementId>,
  ): Result<Withdrawal> = result {
    when (hurdleResponse) {
      is WithdrawalHurdleResponse.TargetWalletAddressHurdleResponse ->
        targetWalletAddressHandler.handleTargetWalletAddressHurdleResponse(
          value,
          hurdleResponse
        ).bind()
      is WithdrawalHurdleResponse.AmountHurdleResponse ->
        amountHandler.handleAmountHurdleResponse(value, hurdleResponse).bind()
      is WithdrawalHurdleResponse.NoteHurdleResponse ->
        noteHandler.handleNoteHurdleResponse(value, hurdleResponse).bind()
      is WithdrawalHurdleResponse.SpeedHurdleResponse ->
        speedHandler.handleSpeedHurdleResponse(
          value,
          hurdleResponse,
          getBalance(value.customerId, value.sourceBalanceToken).bind()
        ).bind()
      is WithdrawalHurdleResponse.ConfirmationHurdleResponse ->
        confirmationHandler.handleConfirmationHurdleResponse(value, hurdleResponse).bind()
      else -> raise(
        IllegalArgumentException("No update function for requirement ID ${hurdleResponse.id}")
      )
    }
  }

  override fun transition(value: Withdrawal): Result<Withdrawal> = result {
    when (value.state) {
      is CollectingInfo -> {
        stateMachine.transitionTo(value, CheckingSanctions).bind()
      }
      else -> raise(IllegalStateException("Unexpected state ${value.state}"))
    }
  }

  override fun goBack(
    value: Withdrawal,
    hurdleResponse: Input.HurdleResponse<RequirementId>
  ): Result<Withdrawal> = result {
    val updatedWithdrawal = when (hurdleResponse.id) {
      SPEED ->
        withdrawalStore.updateWithdrawal(
          value.copy(
            previousAmount = value.amount,
            previousTargetWalletAddress = value.targetWalletAddress,
            previousNote = value.note,
            backCounter = value.backCounter + 1,
            amount = null,
            targetWalletAddress = null,
            note = null,
          )
        ).bind()

      USER_CONFIRMATION ->
        withdrawalStore.updateWithdrawal(
          value.copy(
            previousSelectedSpeed = value.selectedSpeed,
            backCounter = value.backCounter + 1,
            selectedSpeed = null
          )
        ).bind()

      else -> raise(UnsupportedHurdleResultCode(value.id.toString(), ResultCode.BACK))
    }
    updatedWithdrawal
  }

  private fun getBalance(customerId: CustomerId, balanceId: BalanceId): Result<Bitcoins> = result {
    ledgerClient.getBalance(
      customerId,
      balanceId
    ).bind()
  }

  override fun handleFailure(failure: Throwable, value: Withdrawal): Result<Withdrawal> = result {
    failWithdrawal(failure, value).bind()
  }
}

class TargetWalletAddressHandler @Inject constructor(
  private val walletAddressParser: WalletAddressParser,
  private val withdrawalStore: WithdrawalStore,
) {
  fun getHurdle(value: Withdrawal) =
    TargetWalletAddressHurdle(value.previousTargetWalletAddress?.toString())

  fun handleTargetWalletAddressHurdleResponse(
    value: Withdrawal,
    response: WithdrawalHurdleResponse.TargetWalletAddressHurdleResponse
  ): Result<Withdrawal> = result {
    if (response.result != ResultCode.CLEARED) {
      raise(UnsupportedHurdleResultCode(value.customerId.id, response.result))
    }
    val walletAddress = response.walletAddress
      ?: raise(ParameterIsRequired(value.customerId, "targetWalletAddress"))

    withdrawalStore.updateWithdrawal(
      value.copy(targetWalletAddress = walletAddressParser.parse(walletAddress).bind())
    ).bind()
  }
}

class AmountHandler @Inject constructor(
  private val bitcoinAccountClient: BitcoinAccountClient,
  private val limitClient: LimitClient,
  private val validationService: ValidationService,
  private val withdrawalStore: WithdrawalStore,
  @param:Named("withdrawal.amounts.minimum") private val minAmount: Long
) {
  fun getHurdle(value: Withdrawal, currentBalance: Bitcoins) = result {
    val bitcoinAccount = getBitcoinAccount(value.customerId, value.sourceBalanceToken).bind()
    val exchangeRate =
      value.exchangeRate ?: raise(ParameterIsRequired(value.customerId, "exchangeRate"))
    val (daily, weekly) = getRemainingLimits(
      limitClient.getWithdrawalLimits(value.customerId).bind(),
      exchangeRate
    )

    val (maxWithdrawalAmount, reason) = listOf(
      daily.units to DAILY_LIMIT,
      weekly.units to WEEKLY_LIMIT,
      currentBalance.units to BALANCE
    ).minByOrNull { it.first }!!

    AmountHurdle(
      currentBalance = currentBalance,
      displayUnits = bitcoinAccount.currencyDisplayPreference,
      targetWalletAddress = value.targetWalletAddress,
      minimumAmount = Bitcoins(minAmount),
      maximumAmount = Bitcoins(maxWithdrawalAmount),
      maximumAmountReason = reason,
      exchangeRate = exchangeRate,
      maximumAmountFiatEquivalent = bitcoinsToUsd(Bitcoins(maxWithdrawalAmount), exchangeRate),
      previousUserAmount = value.previousAmount,
    )
  }

  fun handleAmountHurdleResponse(
    value: Withdrawal,
    response: WithdrawalHurdleResponse.AmountHurdleResponse
  ): Result<Withdrawal> = result {
    if (response.result != ResultCode.CLEARED) {
      raise(UnsupportedHurdleResultCode(value.customerId.id, response.result))
    }

    val amount = validationService.validateAmount(
      value.customerId,
      value.sourceBalanceToken,
      response.userAmount
    ).bind()

    withdrawalStore.updateWithdrawal(value.copy(amount = amount)).bind()
  }

  private fun getBitcoinAccount(
    customerId: CustomerId,
    balanceId: BalanceId
  ): Result<BitcoinAccount> = result {
    bitcoinAccountClient.getBitcoinAccounts(
      customerId
    ).bind().find { it.balanceId == balanceId } ?: raise(
      IllegalStateException(
        "No Bitcoin account found for customer $customerId for balance $balanceId"
      )
    )
  }

  private fun getRemainingLimits(
    limits: WithdrawalLimitInfo,
    exchangeRate: Money
  ): Pair<Bitcoins, Bitcoins> {
    val remainingDailyLimit = limits.dailyLimit - limits.dailyLimitProgress
    val remainingWeeklyLimit = limits.weeklyLimit - limits.weeklyLimitProgress

    return Pair(
      if (remainingDailyLimit.amount >= BigDecimal.ZERO) {
        usdToBitcoins(remainingDailyLimit, exchangeRate)
      } else {
        Bitcoins.ZERO
      },
      if (remainingWeeklyLimit.amount >= BigDecimal.ZERO) {
        usdToBitcoins(remainingWeeklyLimit, exchangeRate)
      } else {
        Bitcoins.ZERO
      }
    )
  }
}

class NoteHandler @Inject constructor(
  private val validationService: ValidationService,
  private val withdrawalStore: WithdrawalStore,
) {
  fun getHurdle(value: Withdrawal) = NoteHurdle(MAX_NOTE_LENGTH, value.previousNote)

  fun handleNoteHurdleResponse(
    value: Withdrawal,
    response: WithdrawalHurdleResponse.NoteHurdleResponse
  ): Result<Withdrawal> = result {
    when (response.result) {
      ResultCode.CLEARED -> withdrawalStore.updateWithdrawal(
        value.copy(note = validationService.validateNote(value.customerId, response.note).bind())
      ).bind()
      ResultCode.SKIPPED -> value.copy(note = "")
      else -> raise(UnsupportedHurdleResultCode(value.customerId.id, response.result))
    }
  }
}

class SpeedHandler @Inject constructor(
  private val currencyDisplayPreferenceClient: CurrencyDisplayPreferenceClient,
  private val feeQuoteClient: FeeQuoteClient,
  private val withdrawalStore: WithdrawalStore,
  @param:Named("withdrawal.amounts.minimum") val minimum: Long,
  @param:Named("withdrawal.amounts.free_tier_minimum") val amountFreeTierMin: Long,
) {
  fun getHurdles(value: Withdrawal, currentBalance: Bitcoins) = result {
    val exchangeRate =
      value.exchangeRate ?: raise(ParameterIsRequired(value.customerId, "exchangeRate"))
    val targetWalletAddress = value.targetWalletAddress
      ?: raise(ParameterIsRequired(value.customerId, "targetWalletAddress"))
    val amount = value.amount ?: raise(ParameterIsRequired(value.customerId, "amount"))

    val speedOptions: List<WithdrawalSpeedOption> =
      if (value.previousTargetWalletAddress != targetWalletAddress) {
        val onChainFees = feeQuoteClient.quoteOnchainWithdrawalFees(
          customerId = value.customerId.id,
          destinationAddress = targetWalletAddress.toString()
        ).bind()

        val serviceFees = feeQuoteClient.quoteWithdrawalServiceFees(
          customerId = value.customerId.id,
          speeds = onChainFees.map { getSpeedForBlockTarget(it.blockTarget).bind() }
        ).bind()

        withdrawalStore.upsertWithdrawalSpeedOptions(
          value.id,
          onChainFees.map { onChainFee ->
            val blockTarget = onChainFee.blockTarget
            val speed = getSpeedForBlockTarget(blockTarget).bind()
            val serviceFee = serviceFees.first { it.speed == speed }
            val totalFee = when (speed) {
              STANDARD -> Bitcoins(0L)
              else -> Bitcoins(onChainFee.fee.units + serviceFee.fee.value.units)
            }
            val totalFeeFiatEquivalent = bitcoinsToUsd(totalFee, exchangeRate)

            WithdrawalSpeedOption(
              speed = speed,
              totalFee = totalFee,
              totalFeeFiatEquivalent = totalFeeFiatEquivalent,
              serviceFee = serviceFee.fee,
              approximateWaitTime = calculateWaitTime(blockTarget).minutes,
            )
          }
        ).bind()
      } else {
        // If we have gone back and the target wallet address hasn't changed then we can reuse the
        // existing speed options
        withdrawalStore.findSpeedOptionsByWithdrawalToken(value.id).bind()
      }

    // Calculate adjusted amounts for every speed option if required and possible
    val speedHurdle = SpeedHurdle(
      withdrawalSpeedOptions = speedOptions.map {
        addAmountsAndSelectability(it, currentBalance, amount).bind()
      }.map {
        addAdjustedAmount(it, amount, value.exchangeRate as Money).bind()
      },
      currentBalance = currentBalance,
      freeTierMinAmount = Bitcoins(amountFreeTierMin),
      displayUnits = currencyDisplayPreferenceClient.getCurrencyDisplayPreference(
        value.customerId.id
      ).bind().bitcoinDisplayUnits
    )

    // Get the lowest minimum amount and highest maximum amounts from the speed options and fail if
    // it's impossible to withdraw
    val min = speedHurdle.withdrawalSpeedOptions.minOf { it.minimumAmount?.units ?: Long.MAX_VALUE }
    val max = speedHurdle.withdrawalSpeedOptions.maxOf { it.maximumAmount?.units ?: 0 }

    if (min > max) {
      raise(InsufficientBalance(currentBalance, Bitcoins(min), Bitcoins(max), value.amount))
    }

    listOf(speedHurdle)
  }

  fun addAdjustedAmount(
    speedOption: WithdrawalSpeedOption,
    amount: Bitcoins,
    exchangeRate: Money
  ): Result<WithdrawalSpeedOption> = result {
    val isNonStandard = speedOption.speed != STANDARD
    val isSelectable = speedOption.selectable == true
    val belowMinimum = speedOption.minimumAmount?.let { amount < it } ?: false
    val aboveMaximum = speedOption.maximumAmount?.let { amount > it } ?: false

    @Suppress("ComplexCondition")
    if (isNonStandard && isSelectable && (belowMinimum || aboveMaximum)) {
      speedOption.copy(
        adjustedAmount = speedOption.maximumAmount,
        adjustedAmountFiatEquivalent = speedOption.maximumAmount?.let {
          bitcoinsToUsd(it, exchangeRate)
        }
      )
    } else {
      speedOption
    }
  }

  /**
   * Calculates the minimum and maximum amounts for each speed. Also calculates if the speed is
   * selectable.
   */
  fun addAmountsAndSelectability(
    speedOption: WithdrawalSpeedOption,
    currentBalance: Bitcoins,
    amount: Bitcoins
  ): Result<WithdrawalSpeedOption> = result {
    when (speedOption.speed) {
      STANDARD ->
        if (currentBalance.units >= amountFreeTierMin) {
          speedOption.copy(
            minimumAmount = Bitcoins(amountFreeTierMin),
            maximumAmount = currentBalance,
            selectable = isSelectable(
              speedOption,
              amount,
              Bitcoins(amountFreeTierMin),
              Bitcoins(minimum),
              Bitcoins(amountFreeTierMin),
              currentBalance
            )
          )
        } else {
          speedOption.copy(selectable = false)
        }
      else ->
        if (currentBalance.units < amountFreeTierMin) {
          val minAmount = Bitcoins(minimum)
          val maxAmount = currentBalance - speedOption.totalFee
          speedOption.copy(
            minimumAmount = minAmount,
            maximumAmount = maxAmount,
            selectable = isSelectable(
              speedOption,
              amount,
              Bitcoins(amountFreeTierMin),
              Bitcoins(minimum),
              minAmount,
              maxAmount
            )
          )
        } else {
          val minAmount = Bitcoins(minimum)
          val maxAmount = currentBalance - speedOption.totalFee
          speedOption.copy(
            minimumAmount = minAmount,
            maximumAmount = maxAmount,
            selectable = isSelectable(
              speedOption,
              amount,
              Bitcoins(amountFreeTierMin),
              Bitcoins(minimum),
              minAmount,
              maxAmount
            )
          )
        }
    }
  }

  @Suppress("LongParameterList")
  fun isSelectable(
    speedOption: WithdrawalSpeedOption,
    amount: Bitcoins,
    amountFreeTierMin: Bitcoins,
    minimumAmount: Bitcoins,
    calculatedMinAmount: Bitcoins,
    calculatedMaxAmount: Bitcoins,
  ): Boolean? = when (speedOption.speed) {
    STANDARD -> amount >= amountFreeTierMin
    else -> {
      amount >= minimumAmount && calculatedMaxAmount >= calculatedMinAmount
    }
  }

  fun handleSpeedHurdleResponse(
    value: Withdrawal,
    response: WithdrawalHurdleResponse.SpeedHurdleResponse,
    balance: Bitcoins,
  ): Result<Withdrawal> = result {
    if (response.result != ResultCode.CLEARED) {
      raise(UnsupportedHurdleResultCode(value.customerId.id, response.result))
    }

    val amount = value.amount ?: raise(ParameterIsRequired(value.customerId, "amount"))
    val exchangeRate =
      value.exchangeRate ?: raise(ParameterIsRequired(value.customerId, "exchangeRate"))
    val selectedSpeed = response.selectedSpeed
      ?: raise(ParameterIsRequired(value.customerId, "selectedSpeed"))

    val option =
      addAmountsAndSelectability(
        withdrawalStore.findSpeedOptionByWithdrawalTokenAndSpeed(value.id, selectedSpeed).bind()
          ?: raise(
            IllegalStateException(
              "No speed option for speed $selectedSpeed for withdrawal ${value.id}"
            )
          ),
        balance,
        amount
      ).map {
        addAdjustedAmount(it, amount, exchangeRate).bind()
      }.bind()

    if (option.selectable == true) {
      withdrawalStore.updateWithdrawal(
        value.copy(
          selectedSpeed = option,
          amount = option.adjustedAmount ?: value.amount
        )
      ).bind()
    } else {
      raise(AmountBelowFreeTier(value.customerId, amount, Bitcoins(amountFreeTierMin)))
    }
  }

  private fun getSpeedForBlockTarget(blockTarget: Int): Result<WithdrawalSpeed> = result {
    when (blockTarget) {
      WithdrawalSpeedOption.BLOCK_TARGET_2 -> PRIORITY
      WithdrawalSpeedOption.BLOCK_TARGET_12 -> RUSH
      WithdrawalSpeedOption.BLOCK_TARGET_144 -> STANDARD
      else -> raise(InvalidBlockTarget(blockTarget))
    }
  }

  private fun calculateWaitTime(blockTarget: Int): Long =
    blockTarget * AVERAGE_BLOCK_MINING_TIME_MINUTES

  companion object {
    const val AVERAGE_BLOCK_MINING_TIME_MINUTES = 10L
  }
}

class ConfirmationHandler @Inject constructor(
  private val currencyDisplayPreferenceClient: CurrencyDisplayPreferenceClient,
  private val withdrawalStore: WithdrawalStore
) {
  fun getHurdle(value: Withdrawal, currentBalance: Bitcoins) = result {
    WithdrawalHurdle.ConfirmationHurdle(
      selectedSpeed = value.selectedSpeed,
      walletAddress = value.targetWalletAddress,
      amount = value.amount,
      fiatEquivalent = value.fiatEquivalentAmount,
      totalFee = value.selectedSpeed?.totalFee,
      totalFeeFiatEquivalent = value.selectedSpeed?.totalFeeFiatEquivalent,
      total = value.selectedSpeed?.let { value.amount?.plus(it.totalFee) },
      displayUnits = currencyDisplayPreferenceClient.getCurrencyDisplayPreference(
        value.customerId.id
      ).bind().bitcoinDisplayUnits,
      note = value.note,
      currentBalance = currentBalance,
      exchangeRate = value.exchangeRate
    )
  }

  fun handleConfirmationHurdleResponse(
    value: Withdrawal,
    response: WithdrawalHurdleResponse.ConfirmationHurdleResponse
  ): Result<Withdrawal> = result {
    if (response.result != ResultCode.CLEARED) {
      raise(UnsupportedHurdleResultCode(value.customerId.id, response.result))
    }

    withdrawalStore.updateWithdrawal(value.copy(userHasConfirmed = true)).bind()
  }
}
