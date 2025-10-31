package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.StateMachine
import app.cash.quiver.extensions.mapFailure
import arrow.core.raise.result
import xyz.block.bittycity.outie.client.Evaluation
import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.client.SanctionsClient
import xyz.block.bittycity.outie.models.CheckingEligibility
import xyz.block.bittycity.outie.models.CheckingRisk
import xyz.block.bittycity.outie.models.CheckingSanctions
import xyz.block.bittycity.outie.models.CollectingSanctionsInfo
import xyz.block.bittycity.outie.models.FailureReason.SANCTIONS_DECLINED
import xyz.block.bittycity.outie.models.FailureReason.SANCTIONS_FAILED
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Sanctioned
import xyz.block.bittycity.outie.models.SanctionsHeldDecision
import xyz.block.bittycity.outie.models.SanctionsReviewDecision
import xyz.block.bittycity.outie.models.WaitingForSanctionsHeldDecision
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import jakarta.inject.Inject
import xyz.block.domainapi.InfoOnly
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.WarnOnly
import xyz.block.domainapi.util.Operation

class SanctionsController @Inject constructor(
  stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState>,
  withdrawalStore: WithdrawalStore,
  private val sanctionsClient: SanctionsClient,
  private val metricsClient: MetricsClient,
) : WithdrawalController(stateMachine, metricsClient, withdrawalStore) {

  override fun processInputs(
    value: Withdrawal,
    inputs: List<Input<RequirementId>>,
    operation: Operation,
    hurdleGroupId: String?
  ): Result<ProcessingState<Withdrawal, RequirementId>> = result {
    when (value.state) {
      CheckingSanctions -> {
        val targetWalletAddress = requireNotNull(value.targetWalletAddress)

        val sanctionsResult = sanctionsClient.evaluateSanctions(
          customerId = value.customerId.id,
          withdrawalToken = value.id,
          targetWalletAddress = targetWalletAddress,
          value.amount
        ).bind()

        // If we get a successful sanctions response that is HOLD, we shouldn't fail the withdrawal
        // because a case will be created
        when (sanctionsResult) {
          Evaluation.APPROVE -> value.transitionTo(CheckingRisk, metricsClient)
          Evaluation.FAIL -> {
            value.fail(SANCTIONS_FAILED, metricsClient)
            raise(RiskBlocked)
          }
          Evaluation.HOLD -> value.transitionTo(CollectingSanctionsInfo, metricsClient)
        }.mapFailure { e ->
          if (sanctionsResult == Evaluation.HOLD) {
            UnexpectedErrorAfterSanctionsHoldResult(e)
          } else {
            e
          }
        }.bind()
      }

      WaitingForSanctionsHeldDecision, CollectingSanctionsInfo -> {
        processInputs(value, inputs, metricsClient).bind()
      }

      else -> raise(mismatchedState(value))
    }.asProcessingState()
  }

  override fun handleFailure(failure: Throwable, value: Withdrawal): Result<Withdrawal> = result {
    when (value.state) {
      WaitingForSanctionsHeldDecision, CollectingSanctionsInfo -> {
        logger.error(failure) {
          "An unexpected error occurred while processing sanctions information. " +
            "The withdrawal will not be failed."
        }
        raise(failure)
      }

      CheckingSanctions -> {
        when (failure) {
          is UnexpectedErrorAfterSanctionsHoldResult -> {
            logger.error(failure) {
              "An unexpected error occurred after the withdrawal was held for sanctions review. " +
                "The withdrawal will not be failed."
            }
            raise(failure)
          }
          else -> failWithdrawal(failure, value).bind()
        }
      }

      else -> failWithdrawal(failure, value).bind()
    }
  }

  fun processInputs(
    value: Withdrawal,
    inputs: List<Input<RequirementId>>,
    metricsClient: MetricsClient
  ): Result<Withdrawal> = result {
    if (inputs.all { it is Input.ResumeResult }) {
      inputs.filterIsInstance<SanctionsHeldDecision>().firstOrNull()?.let {
        when (it.decision) {
          SanctionsReviewDecision.APPROVE -> value.transitionTo(CheckingEligibility, metricsClient)
          SanctionsReviewDecision.DECLINE -> value.fail(SANCTIONS_DECLINED, metricsClient)
          SanctionsReviewDecision.FREEZE -> value.transitionTo(Sanctioned, metricsClient)
        }.bind()
      } ?: raise(IllegalArgumentException("No decision found for ${value.customerId}"))
    } else {
      raise(IllegalArgumentException("Inputs should be resume results"))
    }
  }
}

data object RiskBlocked : Exception(), InfoOnly
data class UnexpectedErrorAfterSanctionsHoldResult(
  override val cause: Throwable?
) : Exception(cause), WarnOnly
