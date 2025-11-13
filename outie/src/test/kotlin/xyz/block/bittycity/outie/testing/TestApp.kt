package xyz.block.bittycity.outie.testing

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import jakarta.inject.Inject
import jakarta.inject.Named
import org.bitcoinj.base.Address
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import xyz.block.bittycity.outie.api.OnChainWithdrawalDomainApi
import xyz.block.bittycity.outie.fsm.WithdrawalEventBatchProcessor
import xyz.block.bittycity.outie.jooq.JooqTransactor
import xyz.block.bittycity.outie.jooq.JooqWithdrawalOperations
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.common.models.FlatFee
import xyz.block.bittycity.outie.models.LedgerEntryToken
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.models.WithdrawalSpeed.PRIORITY
import xyz.block.bittycity.outie.models.WithdrawalSpeed.RUSH
import xyz.block.bittycity.outie.models.WithdrawalSpeed.STANDARD
import xyz.block.bittycity.outie.models.WithdrawalSpeedOption
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import xyz.block.domainapi.ExecuteResponse
import xyz.block.domainapi.Input
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import org.jooq.DSLContext
import xyz.block.bittycity.outie.store.TestPersistenceModule.Companion.DATASOURCE
import xyz.block.bittycity.outie.store.Transactor
import xyz.block.bittycity.outie.store.WithdrawalOperations

class TestApp @Inject constructor(
  @Named(DATASOURCE) val dslContext: DSLContext,
  @Named("withdrawal.amounts.minimum") val minAmount: Long,
  @Named("withdrawal.amounts.free_tier_minimum") val minAmountFreeTier: Long,
  @Named("withdrawal.supported_countries") val supportedCountries: List<String>,
) {

  @Inject lateinit var data: TestRunData

  // Fakes
  @Inject lateinit var bitcoinAccountService: FakeBitcoinAccountClient
  @Inject lateinit var clock: TestClock
  @Inject lateinit var eligibilityClient: FakeEligibilityClient
  @Inject lateinit var eventClient: FakeEventClient
  @Inject lateinit var exchangeRateClient: FakeExchangeRateClient
  @Inject lateinit var feeQuoteClient: FakeFeeQuoteClient
  @Inject lateinit var ledgerClient: FakeLedgerClient
  @Inject lateinit var limitClient: FakeLimitClient
  @Inject lateinit var onChainService: FakeOnChainClient
  @Inject lateinit var riskService: FakeRiskClient
  @Inject lateinit var sanctionsClient: FakeSanctionsClient
  @Inject lateinit var travelRuleClient: FakeTravelRuleClient

  @Inject lateinit var withdrawalStore: WithdrawalStore
  @Inject lateinit var withdrawalTransactor: Transactor<WithdrawalOperations>
  @Inject lateinit var onChainWithdrawalDomainApi: OnChainWithdrawalDomainApi

  @Inject
  lateinit var withdrawalStateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState>

  @Inject private lateinit var withdrawalEventBatchProcessor: WithdrawalEventBatchProcessor

  fun resetFakes() {
    bitcoinAccountService.reset()
    clock.reset()
    eligibilityClient.reset()
    eventClient.reset()
    exchangeRateClient.reset()
    feeQuoteClient.reset()
    ledgerClient.reset()
    limitClient.reset()
    onChainService.reset()
    riskService.reset()
    sanctionsClient.reset()
    travelRuleClient.reset()
  }

  @Suppress("LongParameterList", "CyclomaticComplexMethod")
  fun TestRunData.seedWithdrawal(
    id: WithdrawalToken? = null,
    state: WithdrawalState = newWithdrawal.state,
    customerId: CustomerId? = null,
    ledgerEntryToken: LedgerEntryToken? = null,
    withdrawalSpeed: WithdrawalSpeed? = null,
    amount: Bitcoins? = null,
    walletAddress: Address? = null,
    reasonForWithdrawal: String? = null,
    selfAttestationDestination: String? = null,
    note: String? = null,
    updatedAt: Instant? = null,
    modifier: (Withdrawal) -> Withdrawal = { it },
  ) = result {
    val prioritySpeedOption = createSpeedOption(id ?: withdrawalToken, PRIORITY).bind()
    val rushSpeedOption = createSpeedOption(id ?: withdrawalToken, RUSH).bind()
    val standardSpeedOption = createSpeedOption(id ?: withdrawalToken, STANDARD).bind()

    val selectedSpeed = when (withdrawalSpeed) {
      RUSH -> rushSpeedOption
      PRIORITY -> prioritySpeedOption
      STANDARD -> standardSpeedOption
      null -> null
    }

    val inserted = withdrawalStore.insertWithdrawal(
      withdrawalToken = id ?: withdrawalToken,
      customerId = customerId ?: customerToken,
      sourceBalanceToken = balanceId,
      state = state,
      source = newWithdrawal.source,
      targetWalletAddress = walletAddress,
      amount = amount,
      ledgerEntryToken = ledgerEntryToken?.token,
      selectedSpeed = selectedSpeed?.id,
      reasonForWithdrawal = reasonForWithdrawal,
      selfAttestationDestination = selfAttestationDestination,
      exchangeRate = exchangeRate,
      note = note,
      updatedAt = updatedAt
    ).getOrThrow()
      .copy(selectedSpeed = selectedSpeed)

    val modified = modifier(inserted)

    if (modified == inserted) {
      inserted
    } else {
      withdrawalStore.updateWithdrawal(modified).getOrThrow()
    }
  }.getOrThrow()

  private fun createSpeedOption(
    id: WithdrawalToken,
    speed: WithdrawalSpeed
  ): Result<WithdrawalSpeedOption> = result {
    withdrawalTransactor.transact("Insert withdrawal speed option") {
      upsertWithdrawalSpeedOption(
        withdrawalToken = id,
        blockTarget = 2,
        speed = speed,
        totalFee = when (speed) {
          PRIORITY -> Bitcoins(3000L)
          RUSH -> Bitcoins(2000L)
          STANDARD -> Bitcoins(0L)
        },
        totalFeeFiatEquivalent = Money.ofMinor(CurrencyUnit.USD, 0),
        serviceFee = FlatFee(Bitcoins(0)),
        approximateWaitTime = 1.minutes,
        selectable = true,
      )
    }.bind()
  }

  fun executeWithdrawalWithHurdleResults(
    withdrawalId: WithdrawalToken,
    allHurdleResponses: List<List<Input.HurdleResponse<RequirementId>>>,
    hurdleGroupId: String? = null
  ): Result<List<ExecuteResponse<WithdrawalToken, RequirementId>>> = result {
    allHurdleResponses.map { hurdleResponses ->
      onChainWithdrawalDomainApi.execute(withdrawalId, hurdleResponses, hurdleGroupId).bind()
    }
  }

  fun processWithdrawalEvents() {
    withdrawalEventBatchProcessor.processBatches().getOrThrow()
  }
}
