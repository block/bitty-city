package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import jakarta.inject.Inject
import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.fsm.Fail
import xyz.block.bittycity.outie.fsm.ScamWarningAcceptRisk
import xyz.block.bittycity.outie.fsm.WithdrawalEffect
import xyz.block.bittycity.outie.models.CollectingScamWarningDecision
import xyz.block.bittycity.outie.models.FailureReason.CUSTOMER_DECLINED_DUE_TO_SCAM_WARNING
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.RequirementId.SCAM_WARNING
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdleResponse
import xyz.block.bittycity.outie.models.WithdrawalNotification
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import xyz.block.domainapi.DomainApiError.UnsupportedHurdleResultCode
import xyz.block.domainapi.Input
import xyz.block.domainapi.ResultCode
import xyz.block.domainapi.UserInteraction
import xyz.block.domainapi.UserInteraction.Hurdle

class ScamWarningController @Inject constructor(
  stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>,
  metricsClient: MetricsClient,
  withdrawalStore: WithdrawalStore
) : WithdrawalInfoCollectionController(
  pendingCollectionState = CollectingScamWarningDecision,
  stateMachine = stateMachine,
  metricsClient = metricsClient,
  withdrawalStore = withdrawalStore,
) {

  override fun findMissingRequirements(value: Withdrawal): Result<List<RequirementId>> = result {
    value.userHasAcceptedRisk?.let { emptyList() } ?: listOf(SCAM_WARNING)
  }

  override fun updateValue(
    value: Withdrawal,
    hurdleResponse: Input.HurdleResponse<RequirementId>,
  ): Result<Withdrawal> = result {
    when (hurdleResponse) {
      is WithdrawalHurdleResponse.ScamWarningHurdleResponse -> {
        when (hurdleResponse.result) {
          ResultCode.CLEARED -> value.copy(userHasAcceptedRisk = true)
          ResultCode.FAILED -> value.copy(userHasAcceptedRisk = false)
          else -> raise(UnsupportedHurdleResultCode(value.customerId.id, hurdleResponse.result))
        }
      }
      else -> raise(mismatchedHurdle(hurdleResponse))
    }
  }

  override fun transition(value: Withdrawal): Result<Withdrawal> = result {
    when (value.userHasAcceptedRisk) {
      true -> stateMachine.transition(value, ScamWarningAcceptRisk()).bind()
      false -> stateMachine.transition(value, Fail(CUSTOMER_DECLINED_DUE_TO_SCAM_WARNING)).bind()
      null -> raise(IllegalStateException("userHasAcceptedRisk is null"))
    }
  }

  override fun getHurdlesForRequirementId(
    requirementId: RequirementId,
    value: Withdrawal,
    previousHurdles: List<Hurdle<RequirementId>>
  ): Result<List<Hurdle<RequirementId>>> = result {
    when (requirementId) {
      SCAM_WARNING -> listOf(WithdrawalHurdle.ScamWarningHurdle)
      else -> emptyList()
    }
  }

  override fun getFinalNotification(
    value: Withdrawal
  ): Result<UserInteraction.Notification<RequirementId>?> = result {
    when (value.userHasAcceptedRisk) {
      false -> WithdrawalNotification.WithdrawalCancelledNotification
      else -> null
    }
  }

  override fun handleFailure(failure: Throwable, value: Withdrawal): Result<Withdrawal> = result {
    failWithdrawal(failure.toFailureReason(), value).bind()
  }
}
