package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import jakarta.inject.Inject
import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.fsm.SanctionsInfoComplete
import xyz.block.bittycity.outie.fsm.WithdrawalEffect
import xyz.block.bittycity.outie.models.CollectingSanctionsInfo
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.RequirementId.SANCTIONS_WITHDRAWAL_REASON
import xyz.block.bittycity.outie.models.WaitingForSanctionsHeldDecision
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalHurdle.WithdrawalReasonHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdleResponse
import xyz.block.bittycity.outie.models.WithdrawalNotification
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import xyz.block.bittycity.outie.validation.ValidationService
import xyz.block.bittycity.outie.validation.ValidationService.Companion.MAX_WITHDRAWAL_REASON_LENGTH
import xyz.block.domainapi.DomainApiError.UnsupportedHurdleResultCode
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.ResultCode
import xyz.block.domainapi.UserInteraction
import xyz.block.domainapi.UserInteraction.Hurdle
import xyz.block.domainapi.util.Operation

class SanctionsInfoCollectionController @Inject constructor(
  stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>,
  private val validationService: ValidationService,
  private val sanctionsController: SanctionsController,
  withdrawalStore: WithdrawalStore,
  metricsClient: MetricsClient
) : WithdrawalInfoCollectionController(
  pendingCollectionState = CollectingSanctionsInfo,
  stateMachine = stateMachine,
  metricsClient = metricsClient,
  withdrawalStore = withdrawalStore,
) {
  override fun findMissingRequirements(value: Withdrawal): Result<List<RequirementId>> = result {
    listOf(
      SANCTIONS_WITHDRAWAL_REASON to { value.reasonForWithdrawal == null },
    ).filter { (_, predicate) -> predicate() }
      .map { (key, _) -> key }
  }

  override fun updateValue(
    value: Withdrawal,
    hurdleResponse: Input.HurdleResponse<RequirementId>,
  ): Result<Withdrawal> = result {
    when (hurdleResponse) {
      is WithdrawalHurdleResponse.WithdrawalReasonHurdleResponse -> {
        when (hurdleResponse.result) {
          ResultCode.CLEARED -> {
            value.copy(
              reasonForWithdrawal = validationService.validateReasonForWithdrawal(
                value.customerId,
                hurdleResponse.reason
              ).bind()
            )
          }
          else -> raise(UnsupportedHurdleResultCode(value.customerId.id, hurdleResponse.result))
        }
      }
      else -> raise(
        IllegalArgumentException("No update function for requirement ID ${hurdleResponse.id}")
      )
    }
  }

  override fun transition(value: Withdrawal): Result<Withdrawal> = result {
    when (value.state) {
      is CollectingSanctionsInfo -> {
        stateMachine.transition(value, SanctionsInfoComplete()).bind()
      }
      else -> raise(IllegalStateException("Unexpected state ${value.state}"))
    }
  }

  override fun getHurdlesForRequirementId(
    requirementId: RequirementId,
    value: Withdrawal,
    previousHurdles: List<Hurdle<RequirementId>>
  ): Result<List<Hurdle<RequirementId>>> = result {
    when (requirementId) {
      SANCTIONS_WITHDRAWAL_REASON -> listOf(WithdrawalReasonHurdle(MAX_WITHDRAWAL_REASON_LENGTH))
      else -> emptyList()
    }
  }

  override fun getFinalNotification(
    value: Withdrawal
  ): Result<UserInteraction.Notification<RequirementId>?> = result {
    when (value.state) {
      WaitingForSanctionsHeldDecision -> WithdrawalNotification.WithdrawalSanctionsHeld
      else -> null
    }
  }

  override fun handleCancelled(
    value: Withdrawal,
    requirementResults: List<Input<RequirementId>>
  ): Result<ProcessingState<Withdrawal, RequirementId>?> = result {
    requirementResults.find { it.result == ResultCode.CANCELLED }?.let {
      // In this case we don't want to fail the withdrawal if the user bails out because we will
      // still get a decision from interdiction
      val updatedValue = stateMachine.transition(value, SanctionsInfoComplete()).bind()
      ProcessingState.Waiting(updatedValue)
    }
  }

  /**
   * We override the default behaviour here because we do not want to fail the withdrawal while we
   * wait for a sanctions decision. Instead, we log this as an error because it shouldn't really
   * happen, and it will likely need to be looked at by on call.
   */
  override fun handleFailure(failure: Throwable, value: Withdrawal): Result<Withdrawal> = result {
    logger.error(failure) {
      "An error occurred while collecting sanctions information. " +
        "The withdrawal will not be failed."
    }
    raise(failure)
  }

  override fun processInputsFromOperation(
    value: Withdrawal,
    inputs: List<Input<RequirementId>>,
    operation: Operation
  ): Result<ProcessingState<Withdrawal, RequirementId>?> = result {
    when (operation) {
      Operation.EXECUTE, Operation.CREATE -> null // normal processing
      /*
       * If the customer abandons the flow but the interdiction case has already been created, the API will get a resume
       * call. However, the state will still be CollectingSanctionsInfo so we need to deal with it in this controller.
       */
      Operation.RESUME -> sanctionsController.processInputs(value, inputs, operation).bind()
    }
  }
}
