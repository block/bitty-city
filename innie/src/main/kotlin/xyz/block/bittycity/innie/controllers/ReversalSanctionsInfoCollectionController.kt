package xyz.block.bittycity.innie.controllers

import app.cash.kfsm.v2.AwaitableStateMachine
import app.cash.kfsm.v2.StateMachine
import arrow.core.raise.result
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject
import xyz.block.bittycity.innie.fsm.DepositEffect
import xyz.block.bittycity.innie.fsm.ReversalSanctionsInfoCollectionComplete
import xyz.block.bittycity.innie.models.CollectingReversalSanctionsInfo
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositReversalHurdle.ReversalReasonHurdle
import xyz.block.bittycity.innie.models.DepositReversalHurdleResponse
import xyz.block.bittycity.innie.models.DepositReversalNotification
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.models.RequirementId.REVERSAL_SANCTIONS_REASON
import xyz.block.bittycity.innie.models.WaitingForReversalSanctionsHeldDecision
import xyz.block.bittycity.innie.store.DepositStore
import xyz.block.bittycity.innie.validation.ParameterIsRequired
import xyz.block.bittycity.innie.validation.ValidationService
import xyz.block.bittycity.innie.validation.ValidationService.MAX_REVERSAL_REASON_LENGTH
import xyz.block.domainapi.DomainApiError.UnsupportedHurdleResultCode
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.ResultCode
import xyz.block.domainapi.UserInteraction

class ReversalSanctionsInfoCollectionController @Inject constructor(
  stateMachine: StateMachine<DepositToken, Deposit, DepositState, DepositEffect>,
  awaitableStateMachine: AwaitableStateMachine<DepositToken, Deposit, DepositState, DepositEffect>,
  depositStore: DepositStore
) : DepositInfoCollectionController(
  pendingCollectionState = CollectingReversalSanctionsInfo,
  stateMachine = stateMachine,
  awaitableStateMachine = awaitableStateMachine,
  depositStore = depositStore
) {

  override val logger: KLogger = KotlinLogging.logger {}

  override fun findMissingRequirements(value: Deposit): Result<List<RequirementId>> = result {
    val currentReversal = value.currentReversal ?: raise(ParameterIsRequired(value.customerId, "currentReversal"))
    listOf(
      REVERSAL_SANCTIONS_REASON to { currentReversal.reasonForReversal == null },
    ).filter { (_, predicate) -> predicate() }
      .map { (key, _) -> key }
  }

  override fun updateValue(
    value: Deposit,
    hurdleResponse: Input.HurdleResponse<RequirementId>,
  ): Result<Deposit> = result {
    when (hurdleResponse) {
      is DepositReversalHurdleResponse.ReversalReasonHurdleResponse -> {
        when (hurdleResponse.result) {
          ResultCode.CLEARED -> {
            depositStore.updateDeposit(
              value.updateCurrentReversal {
                it.copy(reasonForReversal =
                  ValidationService.validateReasonForReversal(
                    value.customerId,
                    hurdleResponse.reason
                  ).bind()
                )
              }.bind()
            )
          }
          else -> raise(UnsupportedHurdleResultCode(value.customerId.id, hurdleResponse.result))
        }.bind()
      }
      else -> raise(
        IllegalArgumentException("No update function for requirement ID ${hurdleResponse.id}")
      )
    }
  }

  override fun transition(value: Deposit): Result<Deposit> = result {
    when (value.state) {
      is CollectingReversalSanctionsInfo -> stateMachine.transition(value, ReversalSanctionsInfoCollectionComplete()).bind()
      else -> raise(IllegalStateException("Unexpected state ${value.state}"))
    }
  }

  override fun getHurdlesForRequirementId(
    requirementId: RequirementId,
    value: Deposit,
    previousHurdles: List<UserInteraction.Hurdle<RequirementId>>
  ): Result<List<UserInteraction.Hurdle<RequirementId>>> = result {
    when (requirementId) {
      REVERSAL_SANCTIONS_REASON -> listOf(ReversalReasonHurdle(MAX_REVERSAL_REASON_LENGTH))
      else -> emptyList()
    }
  }

  override fun getFinalNotification(
    value: Deposit
  ): Result<UserInteraction.Notification<RequirementId>?> = result {
    when (value.state) {
      WaitingForReversalSanctionsHeldDecision -> DepositReversalNotification.DepositReversalSanctionsHeld
      else -> null
    }
  }

  override fun handleCancelled(
    value: Deposit,
    requirementResults: List<Input<RequirementId>>
  ): Result<ProcessingState<Deposit, RequirementId>?> = result {
    requirementResults.find { it.result == ResultCode.CANCELLED }?.let {
      // In this case we don't want to fail the reversal if the user bails out because we will
      // still get a decision from interdiction
      val updatedValue = stateMachine.transition(value, ReversalSanctionsInfoCollectionComplete()).bind()
      ProcessingState.Waiting(updatedValue)
    }
  }

  /**
   * We override the default behaviour here because we do not want to fail the reversal while we
   * wait for a sanctions decision. Instead, we log this as an error because it shouldn't really
   * happen, and it will likely need to be looked at by on call.
   */
  override fun handleFailure(
    failure: Throwable,
    value: Deposit
  ): Result<Deposit> = result {
    logger.error(failure) {
      "An error occurred while collecting sanctions information. The reversal will not be failed."
    }
    raise(failure)
  }
}
