package xyz.block.bittycity.innie.controllers

import app.cash.quiver.extensions.success
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.common.client.Eligibility
import xyz.block.bittycity.innie.api.DepositDomainController
import xyz.block.bittycity.innie.models.CheckingEligibility
import xyz.block.bittycity.innie.models.DepositFailureReason.INELIGIBLE
import xyz.block.bittycity.innie.models.Settled
import xyz.block.bittycity.innie.models.WaitingForReversal
import xyz.block.bittycity.innie.testing.Arbitrary.amount
import xyz.block.bittycity.innie.testing.Arbitrary.customerId
import xyz.block.bittycity.innie.testing.Arbitrary.exchangeRate
import xyz.block.bittycity.innie.testing.Arbitrary.outputIndex
import xyz.block.bittycity.innie.testing.Arbitrary.stringToken
import xyz.block.bittycity.innie.testing.Arbitrary.walletAddress
import xyz.block.bittycity.innie.testing.BittyCityTestCase
import xyz.block.domainapi.util.Operation

class EligibilityControllerTest : BittyCityTestCase() {

  @Inject lateinit var subject: DepositDomainController

  @Test
  fun `Fail when not eligible`() = runTest {
    val deposit = data.seedDeposit(
      state = CheckingEligibility,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
    )

    eligibilityClient.nextEligibilityResult = Eligibility.Ineligible(emptyList()).success()

    subject.execute(deposit, emptyList(), Operation.EXECUTE).shouldBeSuccess()

    depositWithToken(deposit.id) should {
      it.state shouldBe WaitingForReversal
      it.failureReason shouldBe INELIGIBLE
    }
  }

  @Test
  fun `continue if eligible`() = runTest {
    val deposit = data.seedDeposit(
      state = CheckingEligibility,
      customerId = customerId.next(),
      amount = amount.next(),
      exchangeRate = exchangeRate.next(),
      targetWalletAddress = walletAddress.next(),
      blockchainTransactionId = stringToken.next(),
      blockchainTransactionOutputIndex = outputIndex.next(),
      paymentToken = stringToken.next(),
    )

    eligibilityClient.nextEligibilityResult = Eligibility.Eligible(emptyList()).success()

    subject.execute(deposit, emptyList(), Operation.EXECUTE).shouldBeSuccess()

    depositWithToken(deposit.id) should {
      it.state shouldBe Settled
      it.failureReason.shouldBeNull()
    }
  }
}
