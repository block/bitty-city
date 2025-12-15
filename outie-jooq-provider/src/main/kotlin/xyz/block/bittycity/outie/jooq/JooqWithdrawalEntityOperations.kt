package xyz.block.bittycity.outie.jooq

import app.cash.quiver.extensions.catch
import app.cash.quiver.extensions.success
import arrow.core.flatMap
import arrow.core.raise.result
import org.bitcoinj.base.Address
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.jooq.types.ULong
import xyz.block.bittycity.outie.jooq.generated.tables.records.WithdrawalSpeedOptionsRecord
import xyz.block.bittycity.outie.jooq.generated.tables.records.WithdrawalsRecord
import xyz.block.bittycity.outie.jooq.generated.tables.references.WITHDRAWALS
import xyz.block.bittycity.outie.jooq.generated.tables.references.WITHDRAWAL_SPEED_OPTIONS
import xyz.block.bittycity.outie.models.BalanceId
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.common.models.FlatFee
import xyz.block.bittycity.common.models.LedgerTransactionId
import xyz.block.bittycity.common.models.MarginFee
import xyz.block.bittycity.common.models.ServiceFee
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.models.WithdrawalSpeedOption
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalEntityOperations
import xyz.block.bittycity.outie.validation.WalletAddressParser
import xyz.block.domainapi.InfoOnly
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class JooqWithdrawalEntityOperations(
    val context: DSLContext,
    val walletAddressParser: WalletAddressParser,
    val clock: Clock,
) : WithdrawalEntityOperations {

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
    val insertStep = context.insertInto(WITHDRAWALS)
      .set(WITHDRAWALS.TOKEN, withdrawalToken.toString())
      .set(WITHDRAWALS.MERCHANT_TOKEN, customerId.id)
      .set(WITHDRAWALS.STATE, state.name)
      .set(WITHDRAWALS.TARGET_WALLET, targetWalletAddress?.toString())
      .set(WITHDRAWALS.SOURCE_BALANCE_TOKEN, sourceBalanceToken.id)
      .set(WITHDRAWALS.SATOSHIS, amount?.let { ULong.valueOf(it.units) })
      .set(WITHDRAWALS.NOTE, note)
      .set(WITHDRAWALS.LEDGER_ENTRY_TOKEN, ledgerEntryToken)
      .set(WITHDRAWALS.SELECTED_SPEED_REF, selectedSpeed)
      .set(WITHDRAWALS.EXCHANGE_RATE_CURRENCY, exchangeRate?.currencyUnit?.code)
      .set(WITHDRAWALS.EXCHANGE_RATE_UNITS, exchangeRate?.amountMinorLong)
      .set(WITHDRAWALS.REASON_FOR_WITHDRAWAL, reasonForWithdrawal)
      .set(WITHDRAWALS.SELF_ATTESTATION_DESTINATION, selfAttestationDestination)

    createdAt?.let {
      insertStep.set(WITHDRAWALS.CREATED_AT, LocalDateTime.ofInstant(it, ZoneOffset.UTC))
    }

    updatedAt?.let {
      insertStep.set(WITHDRAWALS.UPDATED_AT, LocalDateTime.ofInstant(it, ZoneOffset.UTC))
    }

    val insertedRecord = insertStep
      .returning(withdrawalFields) // fetches the inserted record
      .fetchOne()!!

    insertedRecord.toModel(null).bind()
  }

  @Suppress("LongParameterList")
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
    val selectableByte: Byte? = selectable?.let { if (it) 1.toByte() else 0.toByte() }

    context
      .insertInto(WITHDRAWAL_SPEED_OPTIONS)
      .set(WITHDRAWAL_SPEED_OPTIONS.WITHDRAWAL_TOKEN, withdrawalToken.toString())
      .set(WITHDRAWAL_SPEED_OPTIONS.SPEED, speed.name) // part of the natural key
      .set(WITHDRAWAL_SPEED_OPTIONS.BLOCK_TARGET, blockTarget)
      .set(WITHDRAWAL_SPEED_OPTIONS.TOTAL_FEE, totalFee.units)
      .set(WITHDRAWAL_SPEED_OPTIONS.TOTAL_FEE_FIAT_UNITS, totalFeeFiatEquivalent.amountMinorLong)
      .set(
        WITHDRAWAL_SPEED_OPTIONS.TOTAL_FEE_FIAT_CURRENCY,
        totalFeeFiatEquivalent.currencyUnit.code
      )
      .set(WITHDRAWAL_SPEED_OPTIONS.SERVICE_FEE, serviceFee.value.units)
      .set(WITHDRAWAL_SPEED_OPTIONS.SERVICE_FEE_MARGIN, (serviceFee as? MarginFee)?.margin)
      .set(
        WITHDRAWAL_SPEED_OPTIONS.APPROXIMATE_WAIT_TIME_MINUTES,
        approximateWaitTime.inWholeMinutes.toInt()
      )
      .set(WITHDRAWAL_SPEED_OPTIONS.SELECTABLE, selectableByte)
      // MySQL upsert; relies on a UNIQUE KEY (withdrawal_token, speed)
      .onDuplicateKeyUpdate()
      .set(WITHDRAWAL_SPEED_OPTIONS.BLOCK_TARGET, blockTarget)
      .set(WITHDRAWAL_SPEED_OPTIONS.TOTAL_FEE, totalFee.units)
      .set(WITHDRAWAL_SPEED_OPTIONS.TOTAL_FEE_FIAT_UNITS, totalFeeFiatEquivalent.amountMinorLong)
      .set(
        WITHDRAWAL_SPEED_OPTIONS.TOTAL_FEE_FIAT_CURRENCY,
        totalFeeFiatEquivalent.currencyUnit.code
      )
      .set(WITHDRAWAL_SPEED_OPTIONS.SERVICE_FEE, serviceFee.value.units)
      .set(WITHDRAWAL_SPEED_OPTIONS.SERVICE_FEE_MARGIN, (serviceFee as? MarginFee)?.margin)
      .set(
        WITHDRAWAL_SPEED_OPTIONS.APPROXIMATE_WAIT_TIME_MINUTES,
        approximateWaitTime.inWholeMinutes.toInt()
      )
      .set(WITHDRAWAL_SPEED_OPTIONS.SELECTABLE, selectableByte)
      .execute()

    context
      .selectFrom(WITHDRAWAL_SPEED_OPTIONS)
      .where(
        WITHDRAWAL_SPEED_OPTIONS.WITHDRAWAL_TOKEN.eq(withdrawalToken.toString())
          .and(WITHDRAWAL_SPEED_OPTIONS.SPEED.eq(speed.name))
      )
      .fetchOne()!!.toModel().bind()
  }

  override fun getByToken(token: WithdrawalToken): Result<Withdrawal> = findByToken(token).flatMap {
    it?.let { Result.success(it) }
      ?: Result.failure(WithdrawalNotPresent(token))
  }

  override fun findByToken(token: WithdrawalToken): Result<Withdrawal?> = Result.catch {
    val record = context
      .select(withdrawalFields + withdrawalSpeedOptionsFields)
      .from(WITHDRAWALS)
      .leftJoin(WITHDRAWAL_SPEED_OPTIONS)
      .on(WITHDRAWALS.SELECTED_SPEED_REF.eq(WITHDRAWAL_SPEED_OPTIONS.ID))
      .where(WITHDRAWALS.TOKEN.eq(token.toString()))
      .fetchOne()

    return record?.let { recordToModel(it) }.success()
  }

  override fun getByTokens(tokens: List<WithdrawalToken>): Result<Map<WithdrawalToken, Withdrawal?>> =
    result {
      if (tokens.isEmpty()) raise(WithdrawalTokensEmpty())
      if (tokens.size > MAX_WITHDRAWAL_TOKENS) {
        raise(TooManyWithdrawalTokens(tokens.size, MAX_WITHDRAWAL_TOKENS))
      }

      val tokenStrings = tokens.map { it.toString() }

      val records = context
        .select(withdrawalFields + withdrawalSpeedOptionsFields)
        .from(WITHDRAWALS)
        .leftJoin(WITHDRAWAL_SPEED_OPTIONS)
        .on(WITHDRAWALS.SELECTED_SPEED_REF.eq(WITHDRAWAL_SPEED_OPTIONS.ID))
        .where(WITHDRAWALS.TOKEN.`in`(tokenStrings))
        .fetch()

      val withdrawalsByToken = records.associate { record ->
        val tokenString = record.get(WITHDRAWALS.TOKEN)
        val token = WithdrawalToken.parse(tokenString!!).bind()
        token to recordToModel(record)
      }

      tokens.associateWith { withdrawalsByToken[it] }
    }

  private fun containsSpeedOptionRecord(record: Record): Boolean = record.fields().filter {
    it.qualifiedName.name.contains("withdrawal_speed_options")
  }.map {
    record.getValue(it) != null
  }.contains(true)

  @Suppress("MagicNumber")
  override fun findSpeedOptionsByWithdrawalToken(
    token: WithdrawalToken
  ): Result<List<WithdrawalSpeedOption>> = Result.catch {
    val speedOrder = DSL.choose(WITHDRAWAL_SPEED_OPTIONS.SPEED)
      .`when`(WithdrawalSpeed.PRIORITY.name, 1)
      .`when`(WithdrawalSpeed.RUSH.name, 2)
      .`when`(WithdrawalSpeed.STANDARD.name, 3)
      .otherwise(4)

    context.select(withdrawalSpeedOptionsFields)
      .from(WITHDRAWAL_SPEED_OPTIONS)
      .where(WITHDRAWAL_SPEED_OPTIONS.WITHDRAWAL_TOKEN.eq(token.toString()))
      .orderBy(speedOrder.asc())
      .fetch()
      .map { rec -> toWithdrawalSpeedOptionModel(rec).getOrThrow() }
  }

  override fun findSpeedOptionByWithdrawalTokenAndSpeed(
    token: WithdrawalToken,
    speed: WithdrawalSpeed
  ): Result<WithdrawalSpeedOption?> = Result.catch {
    context.select(withdrawalSpeedOptionsFields)
      .from(WITHDRAWALS, WITHDRAWAL_SPEED_OPTIONS)
      .where(
        WITHDRAWALS.TOKEN.eq(token.toString()),
        WITHDRAWAL_SPEED_OPTIONS.WITHDRAWAL_TOKEN.eq(WITHDRAWALS.TOKEN),
        WITHDRAWAL_SPEED_OPTIONS.SPEED.eq(speed.name)
      )
      .fetch()
      .firstOrNull()
      ?.let { record -> toWithdrawalSpeedOptionModel(record).getOrThrow() }
  }

  override fun hasSeenWalletAddress(wallet: Address): Result<Boolean> = Result.catch {
    context.selectCount()
      .from(WITHDRAWALS)
      .where(WITHDRAWALS.TARGET_WALLET.eq(wallet.toString()))
      .fetchOne(0, Int::class.java)!! > 1
  }

  override fun searchWithdrawals(
    customerId: CustomerId?,
    from: Instant?,
    to: Instant?,
    minAmount: Bitcoins?,
    maxAmount: Bitcoins?,
    states: Set<WithdrawalState>,
    destinationAddress: String?
  ): Result<List<Withdrawal>> = Result.catch {
    val query = context
      .select(withdrawalFields + withdrawalSpeedOptionsFields)
      .from(WITHDRAWALS)
      .leftJoin(WITHDRAWAL_SPEED_OPTIONS)
      .on(WITHDRAWALS.SELECTED_SPEED_REF.eq(WITHDRAWAL_SPEED_OPTIONS.ID))
      .where()

    customerId?.let {
      query.and(WITHDRAWALS.MERCHANT_TOKEN.eq(customerId.toString()))
    }

    // Apply optional time range filters
    from?.let {
      query.and(WITHDRAWALS.CREATED_AT.ge(LocalDateTime.ofInstant(it, ZoneOffset.UTC)))
    }
    to?.let {
      query.and(WITHDRAWALS.CREATED_AT.le(LocalDateTime.ofInstant(it, ZoneOffset.UTC)))
    }

    // Apply optional amount range filters
    minAmount?.let { query.and(WITHDRAWALS.SATOSHIS.ge(ULong.valueOf(it.units))) }
    maxAmount?.let { query.and(WITHDRAWALS.SATOSHIS.le(ULong.valueOf(it.units))) }

    // Apply state filters if any are specified
    if (states.isNotEmpty()) {
      query.and(WITHDRAWALS.STATE.`in`(states.map { it.name }))
    }

    // Apply destination address filter if specified
    destinationAddress?.let { query.and(WITHDRAWALS.TARGET_WALLET.eq(it)) }

    // Execute query and convert results to model objects
    query
      .orderBy(WITHDRAWALS.CREATED_AT.desc())
      .fetch()
      .map { record -> toWithdrawalModel(record, null).getOrThrow() }
  }

  override fun findStuckWithdrawals(
    stuckAfter: Duration,
    retryable: Boolean
  ): Result<List<Withdrawal>> = Result.catch {
    val states = if (retryable) STUCK_RETRYABLE_STATES else STUCK_NON_RETRYABLE_STATES
    val query = context
      .select(withdrawalFields + withdrawalSpeedOptionsFields)
      .from(WITHDRAWALS)
      .leftJoin(WITHDRAWAL_SPEED_OPTIONS)
      .on(WITHDRAWALS.SELECTED_SPEED_REF.eq(WITHDRAWAL_SPEED_OPTIONS.ID))
      .where(WITHDRAWALS.STATE.`in` (states))
      .and(
        WITHDRAWALS.UPDATED_AT.lt(
          LocalDateTime.ofInstant(clock.instant().minus(stuckAfter.toJavaDuration()), ZoneOffset.UTC)
        )
      )

    query
      .orderBy(WITHDRAWALS.CREATED_AT.asc())
      .fetch()
      .map { recordToModel(it) }
  }

  override fun update(withdrawal: Withdrawal): Result<Withdrawal> = result {
    val updateStep = context.update(WITHDRAWALS)
      .set(WITHDRAWALS.VERSION, ULong.valueOf(withdrawal.version + 1))
      .set(WITHDRAWALS.SOURCE_BALANCE_TOKEN, withdrawal.sourceBalanceToken.id)
      .set(WITHDRAWALS.STATE, withdrawal.state.name)
      .set(WITHDRAWALS.SOURCE, withdrawal.source)
      .set(WITHDRAWALS.SATOSHIS, withdrawal.amount?.let { ULong.valueOf(it.units) })
      .set(
        WITHDRAWALS.PREVIOUS_SATOSHIS,
        withdrawal.previousAmount?.let {
          ULong.valueOf(it.units)
        }
      )
      .set(WITHDRAWALS.TARGET_WALLET, withdrawal.targetWalletAddress?.toString())
      .set(WITHDRAWALS.PREVIOUS_TARGET_WALLET, withdrawal.previousTargetWalletAddress?.toString())
      .set(WITHDRAWALS.LEDGER_ENTRY_TOKEN, withdrawal.ledgerTransactionId?.id)
      .set(WITHDRAWALS.SELECTED_SPEED_REF, withdrawal.selectedSpeed?.id)
      .set(WITHDRAWALS.FIAT_VALUE_CURRENCY, withdrawal.fiatEquivalentAmount?.currencyUnit?.code)
      .set(WITHDRAWALS.FIAT_VALUE_UNITS, withdrawal.fiatEquivalentAmount?.amountMinorLong)
      .set(WITHDRAWALS.FAILURE_REASON, withdrawal.failureReason?.name)
      .set(WITHDRAWALS.PROVIDER, withdrawal.provider)
      .set(WITHDRAWALS.BLOCKCHAIN_TRANSACTION_ID, withdrawal.blockchainTransactionId)
      .set(
        WITHDRAWALS.BLOCKCHAIN_TRANSACTION_OUTPUT_INDEX,
        withdrawal.blockchainTransactionOutputIndex
      )
      .set(WITHDRAWALS.NOTE, withdrawal.note)
      .set(WITHDRAWALS.PREVIOUS_NOTE, withdrawal.previousNote)
      .set(WITHDRAWALS.REASON_FOR_WITHDRAWAL, withdrawal.reasonForWithdrawal)
      .set(WITHDRAWALS.USER_HAS_ACCEPTED_RISK, withdrawal.userHasAcceptedRisk?.toByte())
      .set(WITHDRAWALS.STEP_UP_AUTHENTICATED, withdrawal.stepUpAuthenticated?.toByte())
      .set(WITHDRAWALS.FEE_REFUNDED, withdrawal.feeRefunded.toByte())
      .set(WITHDRAWALS.USER_HAS_CONFIRMED, withdrawal.userHasConfirmed?.toByte())
      .set(WITHDRAWALS.SELF_ATTESTATION_DESTINATION, withdrawal.selfAttestationDestination)
      .set(WITHDRAWALS.EXCHANGE_RATE_CURRENCY, withdrawal.exchangeRate?.currencyUnit?.code)
      .set(WITHDRAWALS.EXCHANGE_RATE_UNITS, withdrawal.exchangeRate?.amountMinorLong)
      .set(WITHDRAWALS.BACK_COUNTER, withdrawal.backCounter)
      .where(
        WITHDRAWALS.TOKEN.eq(withdrawal.id.toString())
          .and(WITHDRAWALS.VERSION.eq(ULong.valueOf(withdrawal.version)))
      )

    val updatedRows = updateStep.execute()
    val refreshed = getByToken(withdrawal.id).bind()

    when (updatedRows) {
      0 -> raise(WithdrawalVersionMismatch(withdrawal))
      else -> refreshed
    }
  }

  fun toWithdrawalModel(record: Record, speedOption: WithdrawalSpeedOption?): Result<Withdrawal> =
    result {
      Withdrawal(
        id = WithdrawalToken.parse(record.get(WITHDRAWALS.TOKEN)!!).bind(),
        createdAt = record.get(WITHDRAWALS.CREATED_AT)!!.toInstant(ZoneOffset.UTC),
        updatedAt = record.get(WITHDRAWALS.UPDATED_AT)!!.toInstant(ZoneOffset.UTC),
        version = record.get(WITHDRAWALS.VERSION)!!.toLong(),
        customerId = CustomerId(record.get(WITHDRAWALS.MERCHANT_TOKEN)!!),
        amount = record.get(WITHDRAWALS.SATOSHIS)?.let { Bitcoins(it.toLong()) },
        previousAmount = record.get(WITHDRAWALS.PREVIOUS_SATOSHIS)?.let { Bitcoins(it.toLong()) },
        state = WithdrawalState.byName(record.get<String>(WITHDRAWALS.STATE)).bind(),
        sourceBalanceToken = BalanceId(record.get(WITHDRAWALS.SOURCE_BALANCE_TOKEN)!!),
        targetWalletAddress = record.get(WITHDRAWALS.TARGET_WALLET)?.let {
          walletAddressParser.parse(it).bind()
        },
        previousTargetWalletAddress = record.get(WITHDRAWALS.PREVIOUS_TARGET_WALLET)?.let {
          walletAddressParser.parse(it).bind()
        },
        ledgerTransactionId = record.get(WITHDRAWALS.LEDGER_ENTRY_TOKEN)?.let {
          LedgerTransactionId(it)
        },
        selectedSpeed = speedOption,
        failureReason = record.get(WITHDRAWALS.FAILURE_REASON)?.let {
          Result.catch { FailureReason.valueOf(it) }.bind()
        },
        source = record.get(WITHDRAWALS.SOURCE) ?: Withdrawal.DEFAULT_SOURCE,
        provider = record.get(WITHDRAWALS.PROVIDER),
        blockchainTransactionId = record.get(WITHDRAWALS.BLOCKCHAIN_TRANSACTION_ID),
        blockchainTransactionOutputIndex = record.get(
          WITHDRAWALS.BLOCKCHAIN_TRANSACTION_OUTPUT_INDEX
        ),
        note = record.get(WITHDRAWALS.NOTE),
        previousNote = record.get(WITHDRAWALS.PREVIOUS_NOTE),
        userHasAcceptedRisk = record.get(WITHDRAWALS.USER_HAS_ACCEPTED_RISK)?.let {
          it == 1.toByte()
        },
        stepUpAuthenticated = record.get(WITHDRAWALS.STEP_UP_AUTHENTICATED)?.let {
          it == 1.toByte()
        },
        reasonForWithdrawal = record.get(WITHDRAWALS.REASON_FOR_WITHDRAWAL),
        feeRefunded = record.get(WITHDRAWALS.FEE_REFUNDED) == 1.toByte(),
        userHasConfirmed = record.get(WITHDRAWALS.USER_HAS_CONFIRMED)?.let { it == 1.toByte() },
        selfAttestationDestination = record.get(WITHDRAWALS.SELF_ATTESTATION_DESTINATION),
        exchangeRate = record.get(WITHDRAWALS.EXCHANGE_RATE_CURRENCY)?.let { currency ->
          record.get(WITHDRAWALS.EXCHANGE_RATE_UNITS)?.let { units ->
            Money.ofMinor(CurrencyUnit.of(currency), units)
          }
        },
        backCounter = record.get(WITHDRAWALS.BACK_COUNTER)!!
      )
    }

  fun toWithdrawalSpeedOptionModel(record: Record): Result<WithdrawalSpeedOption> = result {
    WithdrawalSpeedOption(
      id = record.get(WITHDRAWAL_SPEED_OPTIONS.ID)!!,
      speed = Result.catch {
        WithdrawalSpeed.valueOf(record.get(WITHDRAWAL_SPEED_OPTIONS.SPEED)!!)
      }.bind(),
      totalFee = Bitcoins(record.get(WITHDRAWAL_SPEED_OPTIONS.TOTAL_FEE)!!),
      totalFeeFiatEquivalent = Money.ofMinor(
        CurrencyUnit.of(record.get(WITHDRAWAL_SPEED_OPTIONS.TOTAL_FEE_FIAT_CURRENCY)!!),
        record.get(WITHDRAWAL_SPEED_OPTIONS.TOTAL_FEE_FIAT_UNITS)!!
      ),
      serviceFee = record.get(WITHDRAWAL_SPEED_OPTIONS.SERVICE_FEE_MARGIN)?.let { margin ->
        MarginFee(margin, Bitcoins(record.get(WITHDRAWAL_SPEED_OPTIONS.SERVICE_FEE)!!))
      } ?: FlatFee(Bitcoins(record.get(WITHDRAWAL_SPEED_OPTIONS.SERVICE_FEE)!!)),
      approximateWaitTime = record.get(
        WITHDRAWAL_SPEED_OPTIONS.APPROXIMATE_WAIT_TIME_MINUTES
      )!!.minutes
    )
  }

  fun WithdrawalsRecord.toModel(speedOption: WithdrawalSpeedOption? = null): Result<Withdrawal> =
    toWithdrawalModel(this, speedOption)

  fun WithdrawalSpeedOptionsRecord.toModel(): Result<WithdrawalSpeedOption> =
    toWithdrawalSpeedOptionModel(this)

  fun Boolean.toByte(): Byte = if (this) 1.toByte() else 0.toByte()

  private fun recordToModel(record: Record): Withdrawal {
    val speedOption = if (containsSpeedOptionRecord(record)) {
      toWithdrawalSpeedOptionModel(record).getOrThrow()
    } else {
      null
    }
    return toWithdrawalModel(record, speedOption).getOrThrow()
  }

  companion object {
    val STUCK_NON_RETRYABLE_STATES: Set<String> = setOf(
      "COLLECTING_INFO",
      "COLLECTING_SCAM_WARNING_DECISION",
      "COLLECTING_SELF_ATTESTATION"
    )

    val STUCK_RETRYABLE_STATES: Set<String> = setOf(
      "CHECKING_ELIGIBILITY",
      "HOLDING_SUBMISSION",
      "SUBMITTING_ON_CHAIN"
     )

    val withdrawalFields = listOf(
      WITHDRAWALS.ID,
      WITHDRAWALS.VERSION,
      WITHDRAWALS.CREATED_AT,
      WITHDRAWALS.UPDATED_AT,
      WITHDRAWALS.TOKEN,
      WITHDRAWALS.MERCHANT_TOKEN,
      WITHDRAWALS.SOURCE_BALANCE_TOKEN,
      WITHDRAWALS.STATE,
      WITHDRAWALS.SOURCE,
      WITHDRAWALS.SATOSHIS,
      WITHDRAWALS.PREVIOUS_SATOSHIS,
      WITHDRAWALS.TARGET_WALLET,
      WITHDRAWALS.PREVIOUS_TARGET_WALLET,
      WITHDRAWALS.LEDGER_ENTRY_TOKEN,
      WITHDRAWALS.FAILURE_REASON,
      WITHDRAWALS.PROVIDER,
      WITHDRAWALS.BLOCKCHAIN_TRANSACTION_ID,
      WITHDRAWALS.BLOCKCHAIN_TRANSACTION_OUTPUT_INDEX,
      WITHDRAWALS.NOTE,
      WITHDRAWALS.PREVIOUS_NOTE,
      WITHDRAWALS.USER_HAS_ACCEPTED_RISK,
      WITHDRAWALS.STEP_UP_AUTHENTICATED,
      WITHDRAWALS.SELECTED_SPEED_REF,
      WITHDRAWALS.USER_HAS_CONFIRMED,
      WITHDRAWALS.REASON_FOR_WITHDRAWAL,
      WITHDRAWALS.FEE_REFUNDED,
      WITHDRAWALS.SELF_ATTESTATION_DESTINATION,
      WITHDRAWALS.EXCHANGE_RATE_CURRENCY,
      WITHDRAWALS.EXCHANGE_RATE_UNITS,
      WITHDRAWALS.BACK_COUNTER
    )

    val withdrawalSpeedOptionsFields = listOf(
      WITHDRAWAL_SPEED_OPTIONS.ID,
      WITHDRAWAL_SPEED_OPTIONS.VERSION,
      WITHDRAWAL_SPEED_OPTIONS.CREATED_AT,
      WITHDRAWAL_SPEED_OPTIONS.UPDATED_AT,
      WITHDRAWAL_SPEED_OPTIONS.WITHDRAWAL_TOKEN,
      WITHDRAWAL_SPEED_OPTIONS.BLOCK_TARGET,
      WITHDRAWAL_SPEED_OPTIONS.SPEED,
      WITHDRAWAL_SPEED_OPTIONS.TOTAL_FEE,
      WITHDRAWAL_SPEED_OPTIONS.TOTAL_FEE_FIAT_UNITS,
      WITHDRAWAL_SPEED_OPTIONS.TOTAL_FEE_FIAT_CURRENCY,
      WITHDRAWAL_SPEED_OPTIONS.SERVICE_FEE,
      WITHDRAWAL_SPEED_OPTIONS.SERVICE_FEE_MARGIN,
      WITHDRAWAL_SPEED_OPTIONS.APPROXIMATE_WAIT_TIME_MINUTES,
      WITHDRAWAL_SPEED_OPTIONS.SELECTABLE
    )
    private const val MAX_WITHDRAWAL_TOKENS = 1000
  }
}

data class LimitUsage(val dailyUsageCents: Int, val weeklyUsageCents: Int)

sealed class WithdrawalStoreError(message: String) :
  Exception(message),
    InfoOnly

class WithdrawalNotPresent(val withdrawalToken: WithdrawalToken) :
  WithdrawalStoreError("Withdrawal not present: $withdrawalToken")

class WithdrawalTokensEmpty : WithdrawalStoreError("Withdrawal tokens not present")

class TooManyWithdrawalTokens(val withdrawalTokenCount: Int, val withdrawalTokenLimit: Int) :
  WithdrawalStoreError(
    "Too many withdrawal tokens: $withdrawalTokenCount, exceeded limit: $withdrawalTokenLimit"
  )

class WithdrawalVersionMismatch(val withdrawal: Withdrawal) :
  WithdrawalStoreError(
    "Withdrawal not at expected version ${withdrawal.version}: ${withdrawal.id}"
  )
