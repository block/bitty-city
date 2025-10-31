package xyz.block.bittycity.outie.fsm

import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.Arbitrary.amount
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test

class SubmittedOnChainTest : BittyCityTestCase() {

  @Inject lateinit var subject: SubmittedOnChain

  @Test
  fun `successfully submits withdrawal`() = runTest {
    val withdrawal = data.seedWithdrawal(
      withdrawalSpeed = WithdrawalSpeed.RUSH,
      walletAddress = Arbitrary.walletAddress.next(),
      amount = amount.next(),
    )
    subject.effect(withdrawal).getOrThrow() shouldBe withdrawal
  }

  @Test
  fun `fails when target wallet address is missing`() = runTest {
    val withdrawal = data.seedWithdrawal().copy(targetWalletAddress = null)
    subject.effect(withdrawal).shouldBeFailure()
      .message shouldBe "Target wallet address is required"
  }

  @Test
  fun `fails when amount is missing`() = runTest {
    val withdrawal = data.seedWithdrawal().copy(
      amount = null,
      targetWalletAddress = Arbitrary.walletAddress.next()
    )
    subject.effect(withdrawal).shouldBeFailure()
      .message shouldBe "Amount is required"
  }

  @Test
  fun `fails when selected speed is missing`() = runTest {
    val withdrawal = data.seedWithdrawal().copy(
      amount = Arbitrary.amount.next(),
      selectedSpeed = null,
      targetWalletAddress = Arbitrary.walletAddress.next()
    )
    subject.effect(withdrawal).shouldBeFailure()
      .message shouldBe "Selected speed is required"
  }
} 

