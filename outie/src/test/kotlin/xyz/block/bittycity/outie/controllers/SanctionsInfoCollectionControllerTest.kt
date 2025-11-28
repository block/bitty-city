package xyz.block.bittycity.outie.controllers

import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.bittycity.outie.models.CollectingSanctionsInfo
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.WaitingForSanctionsHeldDecision
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalHurdle.WithdrawalReasonHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdleResponse.ConfirmationHurdleResponse
import xyz.block.bittycity.outie.models.WithdrawalNotification
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import xyz.block.bittycity.outie.validation.ValidationService.Companion.MAX_WITHDRAWAL_REASON_LENGTH
import xyz.block.domainapi.DomainApi.Endpoint.SECURE_EXECUTE
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.ResultCode.CLEARED
import xyz.block.domainapi.util.Operation

class SanctionsInfoCollectionControllerTest : BittyCityTestCase() {

  @Inject lateinit var subject: SanctionsInfoCollectionController

  @Test
  fun `returns hurdles when no reason for withdrawal present`() = runTest {
    val withdrawal = data.seedWithdrawal(state = CollectingSanctionsInfo)

    val result = subject.processInputs(
      withdrawal,
      emptyList(),
      Operation.EXECUTE
    ).getOrThrow()
    result shouldBe ProcessingState.UserInteractions(
      hurdles = listOf(WithdrawalReasonHurdle(MAX_WITHDRAWAL_REASON_LENGTH)),
      nextEndpoint = SECURE_EXECUTE
    )
  }

  @Test
  fun `returns hurdles when reason for withdrawal is empty`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CollectingSanctionsInfo,
      reasonForWithdrawal = ""
    )

    val result = subject.processInputs(
      withdrawal,
      emptyList(),
      Operation.EXECUTE
    ).getOrThrow()
    val complete =
      result.shouldBeInstanceOf<ProcessingState.UserInteractions<Withdrawal, RequirementId>>()
    complete.hurdles.size shouldBe 1
    complete.hurdles[0] shouldBe WithdrawalNotification.WithdrawalSanctionsHeld

    withdrawalWithToken(withdrawal.id)
      .state shouldBe WaitingForSanctionsHeldDecision
  }

  @Test
  fun `transitions if reason for withdrawal is present`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CollectingSanctionsInfo,
      reasonForWithdrawal = "I want my free iPhone"
    )

    val result = subject.processInputs(
      withdrawal,
      emptyList(),
      Operation.EXECUTE
    ).getOrThrow()
    val complete =
      result.shouldBeInstanceOf<ProcessingState.UserInteractions<Withdrawal, RequirementId>>()
    complete.hurdles.size shouldBe 1
    complete.hurdles[0] shouldBe WithdrawalNotification.WithdrawalSanctionsHeld

    withdrawalWithToken(withdrawal.id)
      .state shouldBe WaitingForSanctionsHeldDecision
  }

  @Test
  fun `withdrawal is not failed if something fails`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CollectingSanctionsInfo
    )

    subject.processInputs(
      withdrawal,
      listOf(ConfirmationHurdleResponse(CLEARED)), // Force an error - this is an invalid input
      Operation.EXECUTE
    ).shouldBeFailure<DomainApiError.InvalidRequirementResult>()

    withdrawalWithToken(withdrawal.id)
      .state shouldBe CollectingSanctionsInfo
  }
}
