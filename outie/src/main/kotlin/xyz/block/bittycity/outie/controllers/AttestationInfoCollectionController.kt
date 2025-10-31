package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.models.CheckingEligibility
import xyz.block.bittycity.outie.models.CollectingSelfAttestation
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.RequirementId.SELF_ATTESTATION
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdleResponse
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import xyz.block.bittycity.outie.validation.ValidationService
import jakarta.inject.Inject
import xyz.block.domainapi.DomainApiError.UnsupportedHurdleResultCode
import xyz.block.domainapi.Input
import xyz.block.domainapi.ResultCode
import xyz.block.domainapi.UserInteraction.Hurdle

class AttestationInfoCollectionController @Inject constructor(
  stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState>,
  private val validationService: ValidationService,
  metricsClient: MetricsClient,
  withdrawalStore: WithdrawalStore
) : WithdrawalInfoCollectionController(
  pendingCollectionState = CollectingSelfAttestation,
  stateMachine = stateMachine,
  metricsClient = metricsClient,
  withdrawalStore = withdrawalStore,
) {

  override fun transition(value: Withdrawal): Result<Withdrawal> = result {
    when (value.state) {
      is CollectingSelfAttestation -> {
        stateMachine.transitionTo(value, CheckingEligibility).bind()
      }

      else -> raise(mismatchedState(value))
    }
  }

  override fun findMissingRequirements(value: Withdrawal): Result<List<RequirementId>> = result {
    listOf(
      SELF_ATTESTATION to { value.selfAttestationDestination == null },
    ).filter { (_, predicate) -> predicate() }
      .map { (key, _) -> key }
  }

  override fun updateValue(
    value: Withdrawal,
    hurdleResponse: Input.HurdleResponse<RequirementId>
  ): Result<Withdrawal> = result {
    when (hurdleResponse) {
      is WithdrawalHurdleResponse.SelfAttestationHurdleResponse -> {
        when (hurdleResponse.result) {
          ResultCode.CLEARED -> {
            val selfAttestationDestination =
              validationService.validateAttestationDestinationForWithdrawal(
                value.customerId,
                hurdleResponse.destination
              ).bind()
            value.copy(selfAttestationDestination = selfAttestationDestination)
          }

          else -> raise(UnsupportedHurdleResultCode(value.customerId.id, hurdleResponse.result))
        }
      }

      else -> raise(
        IllegalArgumentException("No update function for requirement ID ${hurdleResponse.id}")
      )
    }
  }

  override fun getHurdlesForRequirementId(
    requirementId: RequirementId,
    value: Withdrawal,
    previousHurdles: List<Hurdle<RequirementId>>
  ): Result<List<Hurdle<RequirementId>>> = result {
    when (requirementId) {
      SELF_ATTESTATION -> listOf(WithdrawalHurdle.SelfAttestationHurdle)
      else -> emptyList()
    }
  }

  override fun handleFailure(failure: Throwable, value: Withdrawal): Result<Withdrawal> = result {
    failWithdrawal(failure, value).bind()
  }
}
