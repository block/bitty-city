package xyz.block.bittycity.outie.controllers

import app.cash.quiver.extensions.success
import xyz.block.bittycity.outie.models.CheckingEligibility
import xyz.block.bittycity.outie.models.CheckingTravelRule
import xyz.block.bittycity.outie.models.CollectingSelfAttestation
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import xyz.block.bittycity.outie.testing.shouldBeWithdrawal
import xyz.block.bittycity.common.models.Bitcoins
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.types.shouldBeInstanceOf
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Operation

class TravelRuleControllerTest : BittyCityTestCase() {

  @Inject lateinit var subject: TravelRuleController

  @Test
  fun `if client returns true for requiring attestation should collect attestation`() = runTest {
    travelRuleClient.nextAttestationResult = true.success()
    val withdrawal = data.seedWithdrawal(
      state = CheckingTravelRule,
      amount = Bitcoins(5000L),
      walletAddress = data.targetWalletAddress,
    )
    subject.processInputs(
      withdrawal,
      emptyList(),
      Operation.EXECUTE
    ) shouldBeSuccess {
      val complete = it.shouldBeInstanceOf<ProcessingState.Complete<Withdrawal, RequirementId>>()
      complete.value.shouldBeWithdrawal(
        withdrawal.copy(state = CollectingSelfAttestation)
      )
    }
  }

  @Test
  fun `if client returns false for requiring attestation should not collect attestation`() =
    runTest {
      travelRuleClient.nextAttestationResult = false.success()
      val withdrawal = data.seedWithdrawal(
        state = CheckingTravelRule,
        amount = Bitcoins(5000L),
        walletAddress = data.targetWalletAddress,
      )
      subject.processInputs(
        withdrawal,
        emptyList(),
        Operation.EXECUTE
      ) shouldBeSuccess {
        val complete = it.shouldBeInstanceOf<ProcessingState.Complete<Withdrawal, RequirementId>>()
        complete.value.shouldBeWithdrawal(
          withdrawal.copy(state = CheckingEligibility)
        )
      }
    }

  @Test
  fun `should fail if travel rule client call fails`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CheckingTravelRule,
      amount = Bitcoins(5000L),
      walletAddress = data.targetWalletAddress,
    )
    travelRuleClient.nextAttestationResult = Result.failure(Throwable("Travel rule client error"))

    subject.processInputs(
      withdrawal,
      emptyList(),
      Operation.EXECUTE
    ).shouldBeFailure()
  }
}
