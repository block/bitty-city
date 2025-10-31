package xyz.block.bittycity.outie.store

import xyz.block.bittycity.outie.models.BalanceId
import xyz.block.bittycity.outie.models.Bitcoins
import xyz.block.bittycity.outie.models.CustomerId
import xyz.block.bittycity.outie.models.ServiceFee
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.models.WithdrawalSpeedOption
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import org.bitcoinj.base.Address
import org.joda.money.Money
import java.time.Instant
import kotlin.time.Duration

@Suppress("TooManyFunctions")
interface WithdrawalEntityOperations {

  @Suppress("LongParameterList")
  fun insertWithdrawal(
    withdrawalToken: WithdrawalToken,
    customerId: CustomerId,
    state: WithdrawalState,
    sourceBalanceToken: BalanceId,
    source: String,
    targetWalletAddress: Address? = null,
    amount: Bitcoins? = null,
    note: String? = null,
    createdAt: Instant? = null,
    ledgerEntryToken: String? = null,
    selectedSpeed: Long? = null,
    exchangeRate: Money? = null,
    reasonForWithdrawal: String? = null,
    selfAttestationDestination: String? = null,
    updatedAt: Instant? = null,
  ): Result<Withdrawal>

  @Suppress("LongParameterList")
  fun upsertWithdrawalSpeedOption(
    withdrawalToken: WithdrawalToken,
    blockTarget: Int,
    speed: WithdrawalSpeed,
    totalFee: Bitcoins,
    totalFeeFiatEquivalent: Money,
    serviceFee: ServiceFee,
    approximateWaitTime: Duration,
    selectable: Boolean?
  ): Result<WithdrawalSpeedOption>

  fun getByToken(token: WithdrawalToken): Result<Withdrawal>

  fun findByToken(token: WithdrawalToken): Result<Withdrawal?>

  fun getByTokens(tokens: List<WithdrawalToken>): Result<Map<WithdrawalToken, Withdrawal?>>

  fun findSpeedOptionsByWithdrawalToken(token: WithdrawalToken): Result<List<WithdrawalSpeedOption>>

  fun findSpeedOptionByWithdrawalTokenAndSpeed(
    token: WithdrawalToken,
    speed: WithdrawalSpeed
  ): Result<WithdrawalSpeedOption?>

  fun hasSeenWalletAddress(wallet: Address): Result<Boolean>

  @Suppress("LongParameterList")
  fun searchWithdrawals(
    customerId: CustomerId?,
    from: Instant? = null,
    to: Instant? = null,
    minAmount: Bitcoins? = null,
    maxAmount: Bitcoins? = null,
    states: Set<WithdrawalState> = setOf(),
    destinationAddress: String? = null
  ): Result<List<Withdrawal>>

  fun findStuckWithdrawals(stuckAfter: Duration, retryable: Boolean): Result<List<Withdrawal>>

  fun update(withdrawal: Withdrawal): Result<Withdrawal>
}
