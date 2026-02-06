package xyz.block.bittycity.innie.testing

import app.cash.kfsm.v2.AwaitableStateMachine
import app.cash.kfsm.v2.EffectProcessor
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.bitcoinj.base.Address
import org.joda.money.Money
import xyz.block.bittycity.common.models.BalanceId
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.common.models.LedgerTransactionId
import xyz.block.bittycity.innie.fsm.DepositEffect
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositReversal
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.store.DepositStore
import java.time.Instant

class TestApp {

  @Inject lateinit var data: TestRunData

  @Inject private lateinit var depositStore: DepositStore

  @Inject lateinit var stateMachine: AwaitableStateMachine<DepositToken, Deposit, DepositState, DepositEffect>

  @Inject lateinit var effectProcessor: EffectProcessor<DepositToken, Deposit, DepositState, DepositEffect>

  @Inject lateinit var outbox: InMemoryOutbox<DepositToken, DepositEffect>

  @Inject private lateinit var depositOperations: FakeDepositOperations

  @Inject lateinit var repository: InMemoryDepositRepository

  // Fakes
  @Inject lateinit var eligibilityClient: FakeEligibilityClient
  @Inject lateinit var reversalRiskClient: FakeReversalRiskClient
  @Inject lateinit var riskClient: FakeRiskClient
  @Inject lateinit var sanctionsClient: FakeSanctionsClient

  fun resetFakes() {
    eligibilityClient.reset()
    reversalRiskClient.reset()
    riskClient.reset()
    sanctionsClient.reset()
    outbox.clear()
    depositOperations.clear()
  }

  /**
   * Process all pending effects until no more remain.
   * This is useful for tests that need to wait for multi-step workflows to complete.
   */
  fun processAllEffects() {
    while (effectProcessor.processAll() > 0) {
      // Keep processing until no more effects are pending
    }
  }

  fun TestRunData.seedDeposit(
    id: DepositToken = Arbitrary.depositToken.next(),
    state: DepositState = newDeposit.state,
    updatedAt: Instant? = null,
    customerId: CustomerId,
    amount: Bitcoins,
    exchangeRate: Money,
    targetWalletAddress: Address,
    blockchainTransactionId: String,
    blockchainTransactionOutputIndex: Int,
    paymentToken: String,
    sourceBalanceToken: BalanceId,
    ledgerTransactionId: LedgerTransactionId? = null,
    reversals: List<DepositReversal> = emptyList(),
    modifier: (Deposit) -> Deposit = { it },
  ): Deposit {
    val inserted = depositStore.insertDeposit(
      Deposit(
        id = id,
        state = state,
        updatedAt = updatedAt,
        customerId = customerId,
        amount = amount,
        exchangeRate = exchangeRate,
        targetWalletAddress = targetWalletAddress,
        blockchainTransactionId = blockchainTransactionId,
        blockchainTransactionOutputIndex = blockchainTransactionOutputIndex,
        paymentToken = paymentToken,
        reversals = reversals,
        targetBalanceToken = sourceBalanceToken,
        ledgerTransactionId = ledgerTransactionId
      )
    ).getOrThrow()

    val modified = modifier(inserted)

    return if (modified == inserted) {
      inserted
    } else {
      depositStore.updateDeposit(modified).getOrThrow()
    }
  }

  fun depositWithToken(token: DepositToken): Deposit = depositStore.getDepositByToken(token).getOrThrow()
}
