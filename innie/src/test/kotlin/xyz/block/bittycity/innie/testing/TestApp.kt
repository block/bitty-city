package xyz.block.bittycity.innie.testing

import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.bitcoinj.base.Address
import org.joda.money.Money
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.store.DepositStore
import java.time.Instant

class TestApp {

  @Inject lateinit var data: TestRunData

  @Inject private lateinit var depositStore: DepositStore

  // Fakes
  @Inject lateinit var eligibilityClient: FakeEligibilityClient

  fun resetFakes() {
    eligibilityClient.reset()
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
        paymentToken = paymentToken
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
