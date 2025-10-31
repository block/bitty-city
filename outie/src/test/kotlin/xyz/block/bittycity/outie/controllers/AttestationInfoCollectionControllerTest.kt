package xyz.block.bittycity.outie.controllers

import xyz.block.bittycity.outie.models.CheckingEligibility
import xyz.block.bittycity.outie.models.CollectingSelfAttestation
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalHurdle
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import xyz.block.bittycity.outie.testing.shouldBeWithdrawal
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import xyz.block.domainapi.DomainApi.Endpoint.SECURE_EXECUTE
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Operation
import jakarta.inject.Inject

class AttestationInfoCollectionControllerTest : BittyCityTestCase() {
  @Inject lateinit var subject: AttestationInfoCollectionController

  @Test
  fun `returns hurdles when no self attestation destination for withdrawals present`() = runTest {
    val withdrawal = data.seedWithdrawal(state = CollectingSelfAttestation)

    val result = subject.processInputs(
      withdrawal,
      emptyList(),
      Operation.EXECUTE
    ).getOrThrow()
    result shouldBe ProcessingState.UserInteractions(
      hurdles = listOf(
        WithdrawalHurdle.SelfAttestationHurdle
      ),
      nextEndpoint = SECURE_EXECUTE
    )
  }

  @Test
  fun `returns hurdles when self attestation destination for withdrawal is empty`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CollectingSelfAttestation,
      selfAttestationDestination = "Coinbase"
    )

    val result = subject.processInputs(
      withdrawal,
      emptyList(),
      Operation.EXECUTE
    ).getOrThrow()
    val complete = result.shouldBeInstanceOf<ProcessingState.Complete<Withdrawal, RequirementId>>()
    complete.value.shouldBeWithdrawal(
      withdrawal.copy(
        state = CheckingEligibility
      )
    )
  }

  @Test
  fun `transitions if self attestation destination for withdrawal is present`() = runTest {
    val withdrawal = data.seedWithdrawal(
      state = CollectingSelfAttestation,
      selfAttestationDestination = "Coinbase"
    )

    val result = subject.processInputs(
      withdrawal,
      emptyList(),
      Operation.EXECUTE
    ).getOrThrow()
    val complete = result.shouldBeInstanceOf<ProcessingState.Complete<Withdrawal, RequirementId>>()
    complete.value.shouldBeWithdrawal(
      withdrawal.copy(
        state = CheckingEligibility
      )
    )
  }
}
