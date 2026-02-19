package xyz.block.bittycity.innie.store

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.innie.models.AwaitingDepositConfirmation
import xyz.block.bittycity.innie.models.DepositReversal
import xyz.block.bittycity.innie.testing.Arbitrary.amount
import xyz.block.bittycity.innie.testing.Arbitrary.balanceId
import xyz.block.bittycity.innie.testing.Arbitrary.customerId
import xyz.block.bittycity.innie.testing.Arbitrary.depositReversalToken
import xyz.block.bittycity.innie.testing.Arbitrary.exchangeRate
import xyz.block.bittycity.innie.testing.Arbitrary.outputIndex
import xyz.block.bittycity.innie.testing.Arbitrary.stringToken
import xyz.block.bittycity.innie.testing.Arbitrary.walletAddress
import xyz.block.bittycity.innie.testing.BittyCityTestCase

class DepositStoreSearchTest : BittyCityTestCase() {
  @Inject lateinit var subject: DepositStore

  @Test
  fun `search filters by payment token`() = runTest {
    val searchedPaymentToken = stringToken.next()
    val matching = data.seedDeposit(
      state = AwaitingDepositConfirmation,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = searchedPaymentToken,
      sourceBalanceToken = balanceId.next()
    )
    data.seedDeposit(
      state = AwaitingDepositConfirmation,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
      sourceBalanceToken = balanceId.next()
    )

    val result = subject.search(
      customerId = null,
      paymentToken = searchedPaymentToken
    ).getOrThrow()

    result shouldHaveSize 1
    result.map { it.id } shouldContainExactlyInAnyOrder listOf(matching.id)
  }

  @Test
  fun `search filters by reversal token`() = runTest {
    val searchedReversalToken = depositReversalToken.next()
    val matching = data.seedDeposit(
      state = AwaitingDepositConfirmation,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
      sourceBalanceToken = balanceId.next(),
      reversals = listOf(DepositReversal(token = searchedReversalToken))
    )
    data.seedDeposit(
      state = AwaitingDepositConfirmation,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
      sourceBalanceToken = balanceId.next(),
      reversals = listOf(DepositReversal(token = depositReversalToken.next()))
    )

    val result = subject.search(
      customerId = null,
      reversalToken = searchedReversalToken
    ).getOrThrow()

    result shouldHaveSize 1
    result.map { it.id } shouldContainExactlyInAnyOrder listOf(matching.id)
  }
}
