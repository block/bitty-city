package xyz.block.bittycity.outie.fsm

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import jakarta.inject.Inject
import java.time.Instant
import org.junit.jupiter.api.Test
import xyz.block.bittycity.outie.client.WithdrawalEvent
import xyz.block.bittycity.outie.models.CheckingRisk
import xyz.block.bittycity.outie.models.CheckingTravelRule
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.outie.models.Failed
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.SubmittingOnChain
import xyz.block.bittycity.outie.models.WaitingForPendingConfirmationStatus
import xyz.block.bittycity.outie.models.WithdrawalTransitionEvent
import xyz.block.bittycity.outie.testing.BittyCityTestCase

class WithdrawalEventProcessorTest : BittyCityTestCase() {

  @Inject lateinit var withdrawalEventProcessor: WithdrawalEventProcessor

  @Test
  fun `processes CREATE & UPDATE events successfully`() = runTest {
    // Given a withdrawal and two events
    val withdrawal = data.seedWithdrawal(state = CheckingRisk)

    // Trigger a second event
    val updatedWithdrawal = withdrawalStateMachine.transitionTo(
      withdrawal,
      CheckingTravelRule
    ).getOrThrow()

    processWithdrawalEvents()

    // And the event should be published with UPDATE type
    eventClient.published should { events ->
      events shouldHaveSize 2
      events.first() should {
        it.withdrawalToken shouldBe withdrawal.id
        it.newWithdrawal shouldBe withdrawal
        it.oldWithdrawal shouldBe null
        it.eventType shouldBe WithdrawalEvent.EventType.CREATE
      }
      events.drop(1).first() should {
        it.withdrawalToken shouldBe withdrawal.id
        it.newWithdrawal shouldBe updatedWithdrawal
        it.oldWithdrawal shouldBe withdrawal
        it.eventType shouldBe WithdrawalEvent.EventType.UPDATE
      }
    }
  }

  @Test
  fun `fails when deserializing withdrawal snapshot fails`() = runTest {
    // Given an event with invalid withdrawal snapshot
    val event = WithdrawalTransitionEvent(
      id = 1L,
      createdAt = Instant.now(),
      updatedAt = Instant.now(),
      version = 1L,
      withdrawalId = 1L,
      from = CollectingInfo,
      to = CheckingRisk,
      isProcessed = false,
      withdrawalSnapshot = "invalid json"
    )

    // Then the processing should fail
    withdrawalEventProcessor.processEvent(event).shouldBeFailure()
  }

  @Test
  fun `does not process event if previous event failed`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = SubmittingOnChain,
      walletAddress = data.targetWalletAddress,
      amount = data.bitcoins,
      withdrawalSpeed = data.speed
    )
    val updatedWithdrawal = withdrawalStateMachine.transitionTo(
      withdrawal,
      WaitingForPendingConfirmationStatus
    ).getOrThrow()
    withdrawalStateMachine.transitionTo(
      updatedWithdrawal.copy(failureReason = FailureReason.FAILED_ON_CHAIN),
      Failed
    )

    // There are 3 events to be processed:
    // CREATE SubmittingOnChain
    // UPDATE SubmittingOnChain -> WaitingForPendingConfirmationStatus
    // UPDATE WaitingForPendingConfirmationStatus -> Failed

    // But the effect of the second one will fail
    onChainService.failNextCall = true
    processWithdrawalEvents()

    // So only two will be published because processing the second one fails when trying to submit
    eventClient.published should { events ->
      events.size shouldBe 2
    }

    // We check that the ledger is not called (would happen if the third event gets processed)
    ledgerClient.voidCalls.shouldBeEmpty()
  }
}
