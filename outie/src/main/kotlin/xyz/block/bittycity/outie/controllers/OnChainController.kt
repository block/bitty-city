package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import xyz.block.bittycity.outie.client.CurrencyDisplayPreferenceClient
import xyz.block.bittycity.outie.client.LimitClient
import xyz.block.bittycity.outie.client.LimitResponse
import xyz.block.bittycity.outie.client.LimitViolation
import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.models.ConfirmedComplete
import xyz.block.bittycity.outie.models.ConfirmedOnChain
import xyz.block.bittycity.outie.models.FailedOnChain
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.HoldingSubmission
import xyz.block.bittycity.outie.models.ObservedInMempool
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.SubmittingOnChain
import xyz.block.bittycity.outie.models.WaitingForConfirmedOnChainStatus
import xyz.block.bittycity.outie.models.WaitingForPendingConfirmationStatus
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject
import xyz.block.domainapi.InfoOnly
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Operation

class OnChainController @Inject constructor(
  stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState>,
  withdrawalStore: WithdrawalStore,
  private val limitsClient: LimitClient,
  private val currencyDisplayPreferenceClient: CurrencyDisplayPreferenceClient,
  private val metricsClient: MetricsClient,
) : WithdrawalController(stateMachine, metricsClient, withdrawalStore) {
  override val logger: KLogger = KotlinLogging.logger {}

  override fun processInputs(
    value: Withdrawal,
    inputs: List<Input<RequirementId>>,
    operation: Operation,
    hurdleGroupId: String?
  ): Result<ProcessingState<Withdrawal, RequirementId>> = result {
    when (value.state) {
      HoldingSubmission -> {
        val limitResult = limitsClient.evaluateLimits(value.customerId, value).bind()
        when (limitResult) {
          is LimitResponse.NotLimited -> value.transitionTo(
            SubmittingOnChain,
            metricsClient
          ).bind()
          is LimitResponse.Limited -> {
            value.fail(FailureReason.LIMITED, metricsClient).bind()
            raise(LimitWouldBeExceeded(limitResult.violations))
          }
        }
      }
      SubmittingOnChain -> value.transitionTo(
        WaitingForPendingConfirmationStatus,
        metricsClient
      ).bind()
      WaitingForPendingConfirmationStatus,
      WaitingForConfirmedOnChainStatus -> handleResumeInputs(value, inputs).bind()
      ConfirmedComplete -> {
        logger.info { "Attempted to process inputs $inputs but withdrawal is already complete." }
        value
      }
      else -> raise(mismatchedState(value))
    }.asProcessingState(
      bitcoinDisplayUnits = currencyDisplayPreferenceClient.getCurrencyDisplayPreference(
        value.customerId.id
      ).bind().bitcoinDisplayUnits
    )
  }

  private fun handleResumeInputs(
    value: Withdrawal,
    inputs: List<Input<RequirementId>>
  ): Result<Withdrawal> = result {
    ensureResumeResults(inputs).bind()

    val resumeResult = inputs.find {
      it is ObservedInMempool || it is ConfirmedOnChain || it is FailedOnChain
    } ?: raise(IllegalArgumentException("Unexpected on-chain result result ${value.customerId}"))

    when (resumeResult) {
      is ObservedInMempool -> {
        if (value.state == WaitingForConfirmedOnChainStatus) {
          logger.warn {
            "Received observed in mempool resume result for withdrawal ${value.id} but " +
              "it is already observed in mempool"
          }
          value
        } else {
          value.copy(
            blockchainTransactionId = resumeResult.blockchainTransactionId,
            blockchainTransactionOutputIndex = resumeResult.blockchainTransactionOutputIndex
          )
            .transitionTo(WaitingForConfirmedOnChainStatus, metricsClient).bind()
        }
      }

      is ConfirmedOnChain -> {
        if (value.state == WaitingForPendingConfirmationStatus) {
          logger.warn {
            "Received on-chain confirmation without observing in" +
              " mempool first for withdrawal ${value.id}"
          }
        }
        if (value.blockchainTransactionId != resumeResult.blockchainTransactionId) {
          logger.warn {
            "Blockchain transaction id for on-chain confirmation of withdrawal " +
              "${value.id} does not match current value (current = " +
              "${value.blockchainTransactionId}, new = ${resumeResult.blockchainTransactionId}"
          }
        }
        if (value.blockchainTransactionOutputIndex !=
          resumeResult.blockchainTransactionOutputIndex
        ) {
          logger.warn {
            buildString {
              append("Blockchain transaction output index for on-chain confirmation of ")
              append("withdrawal ${value.id} does not match current value ")
              append("(current = ${value.blockchainTransactionOutputIndex}, new = ")
              append(resumeResult.blockchainTransactionOutputIndex)
            }
          }
        }
        value.copy(
          blockchainTransactionId = resumeResult.blockchainTransactionId,
          blockchainTransactionOutputIndex = resumeResult.blockchainTransactionOutputIndex
        )
          .transitionTo(ConfirmedComplete, metricsClient).bind()
      }

      is FailedOnChain -> {
        logger.warn { "Withdrawal submitted on-chain failed [id=${value.id}]" }
        value.copy(
          blockchainTransactionId = resumeResult.blockchainTransactionId,
          blockchainTransactionOutputIndex = resumeResult.blockchainTransactionOutputIndex
        ).fail(FailureReason.FAILED_ON_CHAIN, metricsClient).bind()
      }

      else -> raise(RuntimeException("Unexpected input $resumeResult"))
    }
  }

  override fun handleFailure(failure: Throwable, value: Withdrawal): Result<Withdrawal> = result {
    when (value.state) {
      WaitingForPendingConfirmationStatus, WaitingForConfirmedOnChainStatus, ConfirmedComplete -> {
        logger.error(failure) {
          "An unexpected error occurred after the withdrawal was submitted on-chain . " +
            "The withdrawal will not be failed."
        }
        raise(failure)
      }

      HoldingSubmission, SubmittingOnChain -> {
        if (value.feeRefunded) {
          logger.error(failure) {
            "An unexpected error occurred while holding the withdrawal for submission after it " +
              "had been held for sanctions review. The withdrawal will not be failed."
          }
          raise(failure)
        } else {
          failWithdrawal(failure, value).bind()
        }
      }

      else -> failWithdrawal(failure, value).bind()
    }
  }

  private fun ensureResumeResults(inputs: List<Input<RequirementId>>) = result {
    if (!inputs.all { it is Input.ResumeResult }) {
      raise(IllegalArgumentException("Inputs should be resume results"))
    }
  }
}

data class LimitWouldBeExceeded(val violations: List<LimitViolation>) :
  Exception(
    "Withdrawal limits would be exceeded: $violations"
  ), InfoOnly
