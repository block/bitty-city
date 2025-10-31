package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.PreconditionNotMet
import xyz.block.bittycity.outie.models.CheckingSanctions
import xyz.block.bittycity.outie.models.Failed
import xyz.block.bittycity.outie.models.FailureReason.CUSTOMER_DECLINED_DUE_TO_SCAM_WARNING
import xyz.block.bittycity.outie.models.LedgerEntryToken
import xyz.block.bittycity.outie.models.LedgerTransactionId
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FailTest : BittyCityTestCase() {

  @Test
  fun `does not void when there's no ledger id`() = runTest {
    val withdrawal = data.seedWithdrawal(state = CheckingSanctions)

    withdrawalStateMachine.transitionTo(
      withdrawal.copy(failureReason = CUSTOMER_DECLINED_DUE_TO_SCAM_WARNING),
      Failed
    ).getOrThrow()
      .state shouldBe Failed

    // Process outbox
    processWithdrawalEvents()

    // And no void call made
    ledgerClient.voidCalls.shouldBeEmpty()
  }

  @Test
  fun `voids when there's a ledger id`() = runTest {
    // Given a withdrawal in a valid state with a ledger transaction
    val ledgerTransactionId = "test-transaction-id"
    val withdrawal = data.seedWithdrawal(
      state = CheckingSanctions,
      ledgerEntryToken = LedgerEntryToken(ledgerTransactionId)
    )

    // When failing the withdrawal
    withdrawalStateMachine.transitionTo(
      withdrawal.copy(failureReason = CUSTOMER_DECLINED_DUE_TO_SCAM_WARNING),
      Failed
    ).getOrThrow()
      .state shouldBe Failed

    // Process outbox
    processWithdrawalEvents()

    // A void call should have been made
    ledgerClient.voidCalls.shouldHaveSingleElement(LedgerTransactionId(ledgerTransactionId))
  }

  @Test
  fun `outbox processing fails to fail the withdrawal if the ledger service fails`() = runTest {
    // Given a withdrawal in a valid state with a ledger transaction
    val ledgerTransactionId = "test-transaction-id"
    val withdrawal = data.seedWithdrawal(
      state = CheckingSanctions,
      ledgerEntryToken = LedgerEntryToken(ledgerTransactionId)
    )

    // And a broken ledger service
    ledgerClient.failNextVoid()

    // Failing the withdrawal should succeed
    withdrawalStateMachine.transitionTo(
      withdrawal.copy(failureReason = CUSTOMER_DECLINED_DUE_TO_SCAM_WARNING),
      Failed
    ).getOrThrow()
      .state shouldBe Failed

    // Processing the outbox succeeds despite failing to fail the withdrawal
    processWithdrawalEvents()

    // And a void call attempted
    ledgerClient.voidCalls.shouldHaveSingleElement(LedgerTransactionId(ledgerTransactionId))
  }

  @Test
  fun `cannot transition to failed if failure reason is not set`() = runTest {
    val withdrawal = data.seedWithdrawal(state = CheckingSanctions)

    withdrawalStateMachine.transitionTo(withdrawal, Failed).shouldBeFailure<PreconditionNotMet>()
      .message shouldBe "Failure reason must be set"
  }

  @Test
  fun `can transition to failed if failure reason is set`() = runTest {
    val withdrawal = data.seedWithdrawal(state = CheckingSanctions)

    withdrawalStateMachine.transitionTo(
      withdrawal.copy(failureReason = CUSTOMER_DECLINED_DUE_TO_SCAM_WARNING),
      Failed
    ).getOrThrow()
      .state shouldBe Failed
  }
}
