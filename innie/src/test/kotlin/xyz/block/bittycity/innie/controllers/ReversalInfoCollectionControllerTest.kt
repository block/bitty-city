package xyz.block.bittycity.innie.controllers

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.innie.models.CheckingReversalSanctions
import xyz.block.bittycity.innie.models.CollectingReversalInfo
import xyz.block.bittycity.innie.models.DepositFailureReason.RISK_BLOCKED
import xyz.block.bittycity.innie.models.DepositReversal
import xyz.block.bittycity.innie.models.DepositReversalHurdle
import xyz.block.bittycity.innie.testing.Arbitrary
import xyz.block.bittycity.innie.testing.Arbitrary.amount
import xyz.block.bittycity.innie.testing.Arbitrary.balanceId
import xyz.block.bittycity.innie.testing.Arbitrary.customerId
import xyz.block.bittycity.innie.testing.Arbitrary.exchangeRate
import xyz.block.bittycity.innie.testing.Arbitrary.outputIndex
import xyz.block.bittycity.innie.testing.Arbitrary.stringToken
import xyz.block.bittycity.innie.testing.Arbitrary.walletAddress
import xyz.block.bittycity.innie.testing.BittyCityTestCase
import xyz.block.domainapi.DomainApi.Endpoint.EXECUTE
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.kfsm.v2.util.Operation

class ReversalInfoCollectionControllerTest : BittyCityTestCase() {

  @Inject lateinit var subject: ReversalInfoCollectionController

  @Test
  fun `returns hurdles when no target wallet address for reversal present`() = runTest {
    val deposit = data.seedDeposit(
      state = CollectingReversalInfo,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
      sourceBalanceToken = balanceId.next(),
      reversals = listOf(
        DepositReversal(
          token = Arbitrary.depositReversalToken.next()
        )
      )
    ) {
      it.copy(failureReason = RISK_BLOCKED)
    }

    val result = subject.processInputs(
      deposit,
      emptyList(),
      Operation.EXECUTE
    ).getOrThrow()
    result shouldBe ProcessingState.UserInteractions(
      hurdles = listOf(DepositReversalHurdle.TargetWalletAddressHurdle),
      nextEndpoint = EXECUTE
    )
  }

  @Test
  fun `transitions if target wallet address and confirmation are present`() = runTest {
    val deposit = data.seedDeposit(
      state = CollectingReversalInfo,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
      sourceBalanceToken = balanceId.next(),
      reversals = listOf(
        DepositReversal(
          token = Arbitrary.depositReversalToken.next(),
          targetWalletAddress = walletAddress.next(),
          userHasConfirmed = true
        )
      )
    ) {
      it.copy(failureReason = RISK_BLOCKED)
    }

    val result = subject.processInputs(
      deposit,
      emptyList(),
      Operation.EXECUTE
    ).getOrThrow()

    result.shouldBeInstanceOf<ProcessingState.Complete<*, *>>()

    depositWithToken(deposit.id).state shouldBe CheckingReversalSanctions
  }
}
