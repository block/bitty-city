package xyz.block.bittycity.outie.store

import app.cash.quiver.extensions.success
import arrow.core.raise.result
import xyz.block.bittycity.outie.models.BalanceId
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.outie.models.ServiceFee
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.models.WithdrawalSpeedOption
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import jakarta.inject.Inject
import org.bitcoinj.base.Address
import org.joda.money.Money
import java.time.Instant
import kotlin.Long
import kotlin.time.Duration

/**
 * Conveniences for single-shot operations on a WithdrawalTransactor.
 */
@Suppress("TooManyFunctions")
class WithdrawalStore @Inject constructor(
  private val withdrawalTransactor: Transactor<WithdrawalOperations>
) {

  fun findWithdrawalByToken(token: WithdrawalToken): Result<Withdrawal?> =
    withdrawalTransactor.transactReadOnly("Find withdrawal by token") {
      findByToken(token)
    }

  fun getWithdrawalByToken(token: WithdrawalToken): Result<Withdrawal> =
    withdrawalTransactor.transactReadOnly("Find withdrawal by token") {
      getByToken(token)
    }

  fun getWithdrawalsByTokens(
    tokens: List<WithdrawalToken>
  ): Result<Map<WithdrawalToken, Withdrawal?>> =
    withdrawalTransactor.transactReadOnly("Find withdrawal by token") {
      getByTokens(tokens)
    }

  fun findSpeedOptionsByWithdrawalToken(
    token: WithdrawalToken
  ): Result<List<WithdrawalSpeedOption>> =
    withdrawalTransactor.transactReadOnly("Find speed options by withdrawal token") {
      findSpeedOptionsByWithdrawalToken(token)
    }

  fun findSpeedOptionByWithdrawalTokenAndSpeed(
    token: WithdrawalToken,
    speed: WithdrawalSpeed
  ): Result<WithdrawalSpeedOption?> =
    withdrawalTransactor.transactReadOnly("Find speed option by withdrawal token and speed") {
      findSpeedOptionByWithdrawalTokenAndSpeed(token, speed)
    }

  fun hasSeenWalletAddress(wallet: Address): Result<Boolean> =
    withdrawalTransactor.transactReadOnly(
      "Checks if wallet address has been used in a previous withdrawal"
    ) {
      hasSeenWalletAddress(wallet)
    }

  fun upsertWithdrawalSpeedOptions(
    withdrawalToken: WithdrawalToken,
    withdrawalSpeedOptions: List<WithdrawalSpeedOption>
  ): Result<List<WithdrawalSpeedOption>> = result {
    withdrawalTransactor.transact("Insert withdrawal speed options") {
      withdrawalSpeedOptions.map { speedOption ->
        upsertWithdrawalSpeedOption(
          withdrawalToken,
          speedOption.speed.blockTarget,
          speedOption.speed,
          speedOption.totalFee,
          speedOption.totalFeeFiatEquivalent,
          speedOption.serviceFee,
          speedOption.approximateWaitTime,
          speedOption.selectable
        ).bind()
      }.success()
    }.bind()
  }

  @Suppress("LongParameterList")
  fun insertWithdrawalSpeedOption(
    withdrawalToken: WithdrawalToken,
    blockTarget: Int,
    speed: WithdrawalSpeed,
    totalFee: Bitcoins,
    totalFeeFiatEquivalent: Money,
    serviceFee: ServiceFee,
    approximateWaitTime: Duration,
    selectable: Boolean? = null,
  ): Result<WithdrawalSpeedOption> =
    withdrawalTransactor.transact("Insert withdrawal speed option") {
      upsertWithdrawalSpeedOption(
        withdrawalToken,
        blockTarget,
        speed,
        totalFee,
        totalFeeFiatEquivalent,
        serviceFee,
        approximateWaitTime,
        selectable
      )
    }

  @Suppress("LongParameterList")
  fun insertWithdrawal(
    withdrawalToken: WithdrawalToken,
    customerId: CustomerId,
    state: WithdrawalState,
    source: String,
    sourceBalanceToken: BalanceId,
    amount: Bitcoins? = null,
    targetWalletAddress: Address? = null,
    note: String? = null,
    createdAt: Instant? = null,
    ledgerEntryToken: String? = null,
    selectedSpeed: Long? = null,
    exchangeRate: Money? = null,
    reasonForWithdrawal: String? = null,
    selfAttestationDestination: String? = null,
    updatedAt: Instant? = null,
  ): Result<Withdrawal> = withdrawalTransactor.transact("Insert withdrawal") {
    result {
      insertWithdrawal(
        withdrawalToken,
        customerId,
        state,
        sourceBalanceToken,
        source,
        targetWalletAddress,
        amount,
        note,
        createdAt,
        ledgerEntryToken,
        selectedSpeed,
        exchangeRate,
        reasonForWithdrawal,
        selfAttestationDestination,
        updatedAt
      ).bind().also {
        insertWithdrawalEvent(withdrawalToken, null, state, it).bind()
      }
    }
  }

  fun updateWithdrawal(withdrawal: Withdrawal): Result<Withdrawal> =
    withdrawalTransactor.transact("Update withdrawal") {
      update(withdrawal)
    }

  @Suppress("LongParameterList")
  fun search(
    customerId: CustomerId?,
    from: Instant? = null,
    to: Instant? = null,
    minAmount: Bitcoins? = null,
    maxAmount: Bitcoins? = null,
    states: Set<WithdrawalState> = setOf(),
    destinationAddress: String? = null
  ): Result<List<Withdrawal>> = withdrawalTransactor.transactReadOnly("Search withdrawals") {
    searchWithdrawals(
      customerId,
      from,
      to,
      minAmount,
      maxAmount,
      states,
      destinationAddress,
    )
  }

  fun findStuckWithdrawals(stuckAfter: Duration, retryable: Boolean) =
    withdrawalTransactor.transactReadOnly("Find stuck withdrawals") {
      findStuckWithdrawals(stuckAfter = stuckAfter, retryable = retryable)
    }
}
