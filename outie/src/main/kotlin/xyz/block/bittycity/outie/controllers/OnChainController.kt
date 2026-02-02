package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import jakarta.inject.Inject
import xyz.block.bittycity.common.client.CurrencyDisplayPreferenceClient
import xyz.block.bittycity.outie.fsm.Fail
import xyz.block.bittycity.outie.fsm.WithdrawalEffect
import xyz.block.bittycity.outie.models.ConfirmedComplete
import xyz.block.bittycity.outie.models.ConfirmedOnChain
import xyz.block.bittycity.outie.models.FailedOnChain
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.ObservedInMempool
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.WaitingForConfirmedOnChainStatus
import xyz.block.bittycity.outie.models.WaitingForPendingConfirmationStatus
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalNotification
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import xyz.block.domainapi.DomainApi
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Operation

class OnChainController @Inject constructor(
  stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>,
  withdrawalStore: WithdrawalStore,
  private val currencyDisplayPreferenceClient: CurrencyDisplayPreferenceClient
) : WithdrawalController(withdrawalStore, stateMachine) {

  override fun processInputs(
    value: Withdrawal,
    inputs: List<Input<RequirementId>>,
    operation: Operation,
    hurdleGroupId: String?
  ): Result<ProcessingState<Withdrawal, RequirementId>> = result {
    when (value.state) {
      WaitingForPendingConfirmationStatus -> {
        val updatedValue = handleResumeInputs(value, inputs).bind()
        ProcessingState.UserInteractions(
          hurdles = listOf(
            WithdrawalNotification.SubmittedOnChainNotification(
              updatedValue.amount!!,
              currencyDisplayPreferenceClient.getCurrencyDisplayPreference(
                updatedValue.customerId.id
              ).bind().bitcoinDisplayUnits,
              updatedValue.fiatEquivalentAmount!!,
              updatedValue.selectedSpeed?.approximateWaitTime!!,
              updatedValue.targetWalletAddress!!
            )
          ),
          nextEndpoint = DomainApi.Endpoint.SECURE_EXECUTE
        )
      }
      WaitingForConfirmedOnChainStatus -> {
        val updatedValue = handleResumeInputs(value, inputs).bind()
        ProcessingState.Waiting(updatedValue)
      }
      ConfirmedComplete -> {
        logger.info { "Attempted to process inputs $inputs but withdrawal is already complete." }
        ProcessingState.Complete(value)
      }
      else -> raise(mismatchedState(value))
    }
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
          stateMachine.transition(
            value.copy(
              blockchainTransactionId = resumeResult.blockchainTransactionId,
              blockchainTransactionOutputIndex = resumeResult.blockchainTransactionOutputIndex
            ),
            xyz.block.bittycity.outie.fsm.ObservedInMempool()
          ).bind()
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
        stateMachine.transition(
          value.copy(
            blockchainTransactionId = resumeResult.blockchainTransactionId,
            blockchainTransactionOutputIndex = resumeResult.blockchainTransactionOutputIndex
          ),
          xyz.block.bittycity.outie.fsm.ConfirmedOnChain()
        ).bind()
      }

      is FailedOnChain -> {
        logger.warn { "Withdrawal submitted on-chain failed [id=${value.id}]" }
        stateMachine.transition(
          value.copy(
            blockchainTransactionId = resumeResult.blockchainTransactionId,
            blockchainTransactionOutputIndex = resumeResult.blockchainTransactionOutputIndex
          ),
          Fail(FailureReason.FAILED_ON_CHAIN)
        ).bind()
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

      else -> failWithdrawal(failure.toFailureReason(), value).bind()
    }
  }

  private fun ensureResumeResults(inputs: List<Input<RequirementId>>) = result {
    if (!inputs.all { it is Input.ResumeResult }) {
      raise(IllegalArgumentException("Inputs should be resume results"))
    }
  }
}
