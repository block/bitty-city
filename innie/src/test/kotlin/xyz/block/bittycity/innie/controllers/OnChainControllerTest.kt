package xyz.block.bittycity.innie.controllers

import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.innie.models.CheckingEligibility
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositResumeResult
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.models.WaitingForDepositConfirmedOnChainStatus
import xyz.block.bittycity.innie.testing.Arbitrary.amount
import xyz.block.bittycity.innie.testing.Arbitrary.customerId
import xyz.block.bittycity.innie.testing.Arbitrary.exchangeRate
import xyz.block.bittycity.innie.testing.Arbitrary.outputIndex
import xyz.block.bittycity.innie.testing.Arbitrary.stringToken
import xyz.block.bittycity.innie.testing.Arbitrary.walletAddress
import xyz.block.bittycity.innie.testing.BittyCityTestCase
import xyz.block.bittycity.innie.testing.shouldBeDeposit
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Operation

class OnChainControllerTest : BittyCityTestCase() {

  @Inject lateinit var subject: OnChainController

  @Test
  fun `should transition when withdrawal is confirmed in the blockchain`() = runTest {
    val deposit = data.seedDeposit(
      state = WaitingForDepositConfirmedOnChainStatus,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
    )

    val resumeResult = DepositResumeResult.ConfirmedOnChain(
      depositToken = deposit.id,
      paymentToken = deposit.paymentToken,
      targetWalletAddress = deposit.targetWalletAddress,
      amount = deposit.amount,
      blockchainTransactionId = deposit.blockchainTransactionId,
      blockchainTransactionOutputIndex = deposit.blockchainTransactionOutputIndex,
    )

    val updated = subject.processInputs(
      deposit,
      listOf(resumeResult),
      Operation.RESUME
    ).getOrThrow()
      .shouldBeInstanceOf<ProcessingState.Complete<Deposit, RequirementId>>()

    updated.value shouldBeDeposit deposit.copy(
      state = CheckingEligibility,
      blockchainTransactionId = resumeResult.blockchainTransactionId,
      blockchainTransactionOutputIndex = resumeResult.blockchainTransactionOutputIndex
    )
  }
}
