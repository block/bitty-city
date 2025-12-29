package xyz.block.bittycity.innie.controllers

import app.cash.kfsm.StateMachine
import app.cash.quiver.extensions.mapFailure
import arrow.core.raise.result
import jakarta.inject.Inject
import xyz.block.bittycity.common.client.Evaluation
import xyz.block.bittycity.common.client.RiskBlocked
import xyz.block.bittycity.common.client.SanctionsClient
import xyz.block.bittycity.innie.client.MetricsClient
import xyz.block.bittycity.innie.models.CheckingReversalRisk
import xyz.block.bittycity.innie.models.CheckingSanctions
import xyz.block.bittycity.innie.models.CollectingSanctionsInfo
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositReversalFailureReason.SANCTIONS_FAILED
import xyz.block.bittycity.innie.models.DepositReversalToken
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.models.WaitingForSanctionsHeldDecision
import xyz.block.bittycity.innie.store.DepositStore
import xyz.block.bittycity.innie.validation.ParameterIsRequired
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.WarnOnly
import xyz.block.domainapi.util.Operation

class ReversalSanctionsController @Inject constructor(
  stateMachine: StateMachine<DepositToken, Deposit, DepositState>,
  depositStore: DepositStore,
  private val sanctionsClient: SanctionsClient<DepositReversalToken>,
  private val metricsClient: MetricsClient,
): DepositController(stateMachine, metricsClient, depositStore) {
  override fun processInputs(
    value: Deposit,
    inputs: List<Input<RequirementId>>,
    operation: Operation,
    hurdleGroupId: String?
  ): Result<ProcessingState<Deposit, RequirementId>> = result {
    when (value.state) {
      CheckingSanctions -> {
        val reversal = value.currentReversal ?: raise(ParameterIsRequired(value.customerId, "currentReversal"))
        val targetWalletAddress = requireNotNull(reversal.targetWalletAddress)

        val sanctionsResult = sanctionsClient.evaluateSanctions(
          customerId = value.customerId.id,
          transactionToken = reversal.token,
          targetWalletAddress = targetWalletAddress,
          amount = value.amount
        ).bind()

        // If we get a successful sanctions response that is HOLD, we shouldn't fail the reversal
        // because a case will be created
        when (sanctionsResult) {
          Evaluation.APPROVE -> value.transitionTo(CheckingReversalRisk, metricsClient)
          Evaluation.FAIL -> {
            value.failReversal(SANCTIONS_FAILED, metricsClient)
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
      else -> raise(mismatchedState(value))
    }.asProcessingState()
  }

  override fun handleFailure(
    failure: Throwable,
    value: Deposit
  ): Result<Deposit> = result {
    when (value.state) {
      WaitingForSanctionsHeldDecision, CollectingSanctionsInfo -> {
        logger.error(failure) {
          "An unexpected error occurred while processing sanctions information. " +
                  "The reversal will not be failed."
        }
        raise(failure)
      }

      CheckingSanctions -> {
        when (failure) {
          is UnexpectedErrorAfterSanctionsHoldResult -> {
            logger.error(failure) {
              "An unexpected error occurred after the reversal was held for sanctions review. " +
                      "The reversal will not be failed."
            }
            raise(failure)
          }
          else -> failReversal(failure, value).bind()
        }
      }

      else -> failReversal(failure, value).bind()
    }
  }
}

data class UnexpectedErrorAfterSanctionsHoldResult(
  override val cause: Throwable?
) : Exception(cause), WarnOnly
