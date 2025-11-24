package xyz.block.bittycity.innie.controllers

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import jakarta.inject.Inject
import xyz.block.bittycity.common.client.CurrencyDisplayPreferenceClient
import xyz.block.bittycity.innie.client.MetricsClient
import xyz.block.bittycity.innie.models.CheckingEligibility
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositResumeResult.ConfirmedOnChain
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.models.WaitingForDepositConfirmedOnChainStatus
import xyz.block.bittycity.innie.store.DepositStore
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Operation

class OnChainController @Inject constructor(
  stateMachine: StateMachine<DepositToken, Deposit, DepositState>,
  depositStore: DepositStore,
  private val currencyDisplayPreferenceClient: CurrencyDisplayPreferenceClient,
  private val metricsClient: MetricsClient,
) : DepositController(stateMachine, metricsClient, depositStore) {

  override fun processInputs(
    value: Deposit,
    inputs: List<Input<RequirementId>>,
    operation: Operation,
    hurdleGroupId: String?
  ): Result<ProcessingState<Deposit, RequirementId>> = result {
    when (value.state) {
      WaitingForDepositConfirmedOnChainStatus -> handleResumeInputs(value, inputs).bind()
      else -> raise(mismatchedState(value))
    }.asProcessingState(
      bitcoinDisplayUnits = currencyDisplayPreferenceClient.getCurrencyDisplayPreference(
        value.customerId.id
      ).bind().bitcoinDisplayUnits
    )
  }

  /**
   * This controller waits for calls to the resume endpoint that indicate when a deposit is confirmed or, in rare cases,
   * voided. Any unexpected failures should not attempt the deposit but rather just log.
   */
  override fun handleFailure(
    failure: Throwable,
    value: Deposit
  ): Result<Deposit> = result {
    logger.warn(failure) {
      "An unexpected error occurred waiting for a confirmation of an on-chain deposit. The deposit will not be failed."
    }
    raise(failure)
  }

  private fun handleResumeInputs(
    value: Deposit,
    inputs: List<Input<RequirementId>>
  ): Result<Deposit> = result {
    ensureResumeResults(inputs).bind()
    val resumeResult = inputs.find {
      it is ConfirmedOnChain
    } ?: raise(IllegalArgumentException("Unexpected on-chain resume result ${value.customerId}"))

    when (resumeResult) {
      is ConfirmedOnChain -> {
        if (resumeResult.depositToken != value.id ||
          resumeResult.amount != value.amount ||
          resumeResult.paymentToken != value.paymentToken ||
          resumeResult.targetWalletAddress != value.targetWalletAddress ||
          resumeResult.blockchainTransactionId != value.blockchainTransactionId ||
          resumeResult.blockchainTransactionOutputIndex != value.blockchainTransactionOutputIndex) {
          logger.warn {
            buildString {
              append("Current on-chain deposit does not match information in on-chain confirmation resume result: ")
              append("current=[$value] resume_result=[$resumeResult]")
            }
          }
        }
        value.transitionTo(CheckingEligibility, metricsClient).bind()
      }
      else ->  raise(RuntimeException("Unexpected input $resumeResult"))
    }
  }

  private fun ensureResumeResults(inputs: List<Input<RequirementId>>) = result {
    if (!inputs.all { it is Input.ResumeResult }) {
      raise(IllegalArgumentException("Inputs should be resume results"))
    }
  }
}
