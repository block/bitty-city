package xyz.block.bittycity.outie.testing.fakes

import arrow.core.raise.result
import com.squareup.moshi.Moshi
import org.bitcoinj.base.Address
import org.joda.money.Money
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.common.models.LedgerTransactionId
import xyz.block.bittycity.common.models.ServiceFee
import xyz.block.bittycity.outie.json.WithdrawalMoshi
import xyz.block.bittycity.common.models.BalanceId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.models.WithdrawalSpeedOption
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.models.WithdrawalTransitionEvent
import xyz.block.bittycity.outie.store.WithdrawalNotPresent
import xyz.block.bittycity.outie.store.WithdrawalOperations
import xyz.block.bittycity.outie.store.WithdrawalTokensEmpty
import xyz.block.bittycity.outie.store.WithdrawalVersionMismatch
import xyz.block.bittycity.outie.store.TooManyWithdrawalTokens
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class FakeWithdrawalOperations(
  private val clock: Clock = Clock.systemUTC(),
  private val moshi: Moshi = WithdrawalMoshi.create()
) : WithdrawalOperations {

  private val withdrawals = ConcurrentHashMap<WithdrawalToken, Withdrawal>()
  private val speedOptions = ConcurrentHashMap<WithdrawalToken, MutableMap<WithdrawalSpeed, WithdrawalSpeedOption>>()
  private val events = ConcurrentHashMap<Long, WithdrawalTransitionEvent>()
  private val withdrawalTokenToId = ConcurrentHashMap<WithdrawalToken, Long>()

  private val speedOptionIdGenerator = AtomicLong(0)
  private val eventIdGenerator = AtomicLong(0)
  private val withdrawalIdGenerator = AtomicLong(0)

  fun reset() {
    withdrawals.clear()
    speedOptions.clear()
    events.clear()
    withdrawalTokenToId.clear()
    speedOptionIdGenerator.set(0)
    eventIdGenerator.set(0)
    withdrawalIdGenerator.set(0)
  }

  override fun insertWithdrawal(
    withdrawalToken: WithdrawalToken,
    customerId: CustomerId,
    state: WithdrawalState,
    sourceBalanceToken: BalanceId,
    source: String,
    targetWalletAddress: Address?,
    amount: Bitcoins?,
    note: String?,
    createdAt: Instant?,
    ledgerEntryToken: String?,
    selectedSpeed: Long?,
    exchangeRate: Money?,
    reasonForWithdrawal: String?,
    selfAttestationDestination: String?,
    updatedAt: Instant?,
  ): Result<Withdrawal> = result {
    val now = clock.instant()
    val selectedSpeedOption = selectedSpeed?.let { speedId ->
      speedOptions.values
        .flatMap { it.values }
        .find { it.id == speedId }
    }

    val withdrawal = Withdrawal(
      id = withdrawalToken,
      createdAt = createdAt ?: now,
      updatedAt = updatedAt ?: now,
      version = 1L,
      customerId = customerId,
      state = state,
      sourceBalanceToken = sourceBalanceToken,
      targetWalletAddress = targetWalletAddress,
      amount = amount,
      note = note,
      selectedSpeed = selectedSpeedOption,
      exchangeRate = exchangeRate,
      reasonForWithdrawal = reasonForWithdrawal,
      selfAttestationDestination = selfAttestationDestination,
      source = source,
      ledgerTransactionId = ledgerEntryToken?.let { LedgerTransactionId(it) },
    )

    val existing = withdrawals.putIfAbsent(withdrawalToken, withdrawal)
    if (existing != null) {
      raise(IllegalStateException("Withdrawal with token $withdrawalToken already exists"))
    }
    withdrawalTokenToId[withdrawalToken] = withdrawalIdGenerator.incrementAndGet()
    withdrawal
  }

  override fun upsertWithdrawalSpeedOption(
    withdrawalToken: WithdrawalToken,
    blockTarget: Int,
    speed: WithdrawalSpeed,
    totalFee: Bitcoins,
    totalFeeFiatEquivalent: Money,
    serviceFee: ServiceFee,
    approximateWaitTime: Duration,
    selectable: Boolean?
  ): Result<WithdrawalSpeedOption> = result {
    val tokenSpeedOptions = speedOptions.getOrPut(withdrawalToken) { ConcurrentHashMap() }
    val existing = tokenSpeedOptions[speed]

    val speedOption = WithdrawalSpeedOption(
      id = existing?.id ?: speedOptionIdGenerator.incrementAndGet(),
      speed = speed,
      totalFee = totalFee,
      totalFeeFiatEquivalent = totalFeeFiatEquivalent,
      serviceFee = serviceFee,
      approximateWaitTime = approximateWaitTime,
      selectable = selectable
    )

    tokenSpeedOptions[speed] = speedOption
    speedOption
  }

  override fun getByToken(token: WithdrawalToken): Result<Withdrawal> = result {
    withdrawals[token] ?: raise(WithdrawalNotPresent(token))
  }

  override fun findByToken(token: WithdrawalToken): Result<Withdrawal?> = result {
    withdrawals[token]
  }

  override fun getByTokens(tokens: List<WithdrawalToken>): Result<Map<WithdrawalToken, Withdrawal?>> = result {
    if (tokens.isEmpty()) raise(WithdrawalTokensEmpty())
    if (tokens.size > MAX_WITHDRAWAL_TOKENS) {
      raise(TooManyWithdrawalTokens(tokens.size, MAX_WITHDRAWAL_TOKENS))
    }
    tokens.associateWith { withdrawals[it] }
  }

  override fun findSpeedOptionsByWithdrawalToken(token: WithdrawalToken): Result<List<WithdrawalSpeedOption>> = result {
    val tokenSpeedOptions = speedOptions[token] ?: emptyMap()
    tokenSpeedOptions.values
      .sortedBy { speedOption ->
        when (speedOption.speed) {
          WithdrawalSpeed.PRIORITY -> 1
          WithdrawalSpeed.RUSH -> 2
          WithdrawalSpeed.STANDARD -> 3
        }
      }
  }

  override fun findSpeedOptionByWithdrawalTokenAndSpeed(
    token: WithdrawalToken,
    speed: WithdrawalSpeed
  ): Result<WithdrawalSpeedOption?> = result {
    speedOptions[token]?.get(speed)
  }

  override fun hasSeenWalletAddress(wallet: Address): Result<Boolean> = result {
    withdrawals.values.count { it.targetWalletAddress == wallet } > 1
  }

  override fun searchWithdrawals(
    customerId: CustomerId?,
    from: Instant?,
    to: Instant?,
    minAmount: Bitcoins?,
    maxAmount: Bitcoins?,
    states: Set<WithdrawalState>,
    destinationAddress: String?
  ): Result<List<Withdrawal>> = result {
    withdrawals.values.filter { withdrawal ->
      (customerId == null || withdrawal.customerId == customerId) &&
        (from == null || !withdrawal.createdAt.isBefore(from)) &&
        (to == null || !withdrawal.createdAt.isAfter(to)) &&
        (minAmount == null || (withdrawal.amount != null && withdrawal.amount.units >= minAmount.units)) &&
        (maxAmount == null || (withdrawal.amount != null && withdrawal.amount.units <= maxAmount.units)) &&
        (states.isEmpty() || withdrawal.state in states) &&
        (destinationAddress == null || withdrawal.targetWalletAddress?.toString() == destinationAddress)
    }
  }

  override fun findStuckWithdrawals(stuckAfter: Duration, retryable: Boolean): Result<List<Withdrawal>> = result {
    val stuckStates = if (retryable) STUCK_RETRYABLE_STATES else STUCK_NON_RETRYABLE_STATES
    val cutoff = clock.instant().minus(stuckAfter.toJavaDuration())

    withdrawals.values
      .filter { it.state.name in stuckStates && it.updatedAt.isBefore(cutoff) }
      .sortedBy { it.createdAt }
  }

  override fun update(withdrawal: Withdrawal): Result<Withdrawal> = result {
    val existing = withdrawals[withdrawal.id]
      ?: raise(WithdrawalNotPresent(withdrawal.id))

    if (existing.version != withdrawal.version) {
      raise(WithdrawalVersionMismatch(withdrawal))
    }

    val updated = withdrawal.copy(
      version = withdrawal.version + 1,
      updatedAt = clock.instant()
    )
    withdrawals[withdrawal.id] = updated
    updated
  }

  override fun insertWithdrawalEvent(
    withdrawalToken: WithdrawalToken,
    fromState: WithdrawalState?,
    toState: WithdrawalState,
    withdrawalSnapshot: Withdrawal,
  ): Result<WithdrawalTransitionEvent> = result {
    val withdrawalId = withdrawalTokenToId[withdrawalToken]
      ?: raise(IllegalStateException("Withdrawal not found: $withdrawalToken"))

    val eventId = eventIdGenerator.incrementAndGet()
    val now = clock.instant()
    val adapter = moshi.adapter(Withdrawal::class.java)
    val event = WithdrawalTransitionEvent(
      id = eventId,
      createdAt = now,
      updatedAt = now,
      version = 1L,
      withdrawalId = withdrawalId,
      from = fromState,
      to = toState,
      isProcessed = false,
      withdrawalSnapshot = adapter.toJson(withdrawalSnapshot)
    )
    events[eventId] = event
    event
  }

  override fun fetchUnprocessedEvents(batchSize: Int): Result<List<WithdrawalTransitionEvent>> = result {
    require(batchSize > 0) { "Batch size must be positive" }
    events.values
      .filter { !it.isProcessed }
      .sortedBy { it.id }
      .take(batchSize)
  }

  override fun markEventAsProcessed(eventId: Long): Result<Unit> = result {
    val event = events[eventId]
      ?: raise(IllegalStateException("Withdrawal event not found: $eventId"))
    events[eventId] = event.copy(isProcessed = true)
  }

  override fun fetchPreviousEvent(
    currentEvent: WithdrawalTransitionEvent
  ): Result<WithdrawalTransitionEvent?> = result {
    events.values
      .filter { it.withdrawalId == currentEvent.withdrawalId && it.id < currentEvent.id }
      .maxByOrNull { it.id }
  }

  companion object {
    private val STUCK_NON_RETRYABLE_STATES = setOf(
      "COLLECTING_INFO",
      "COLLECTING_SCAM_WARNING_DECISION",
      "COLLECTING_SELF_ATTESTATION"
    )

    private val STUCK_RETRYABLE_STATES = setOf(
      "CHECKING_ELIGIBILITY",
      "HOLDING_SUBMISSION",
      "SUBMITTING_ON_CHAIN"
    )

    private const val MAX_WITHDRAWAL_TOKENS = 1000
  }
}
