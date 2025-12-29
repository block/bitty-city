package xyz.block.bittycity.innie.controllers

import app.cash.kfsm.StateMachine
import app.cash.quiver.extensions.catch
import arrow.core.raise.result
import jakarta.inject.Inject
import xyz.block.bittycity.common.client.CurrencyDisplayPreferenceClient
import xyz.block.bittycity.common.utils.WalletAddressParser
import xyz.block.bittycity.innie.client.MetricsClient
import xyz.block.bittycity.innie.models.CheckingSanctions
import xyz.block.bittycity.innie.models.CollectingInfo
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositReversalHurdle
import xyz.block.bittycity.innie.models.DepositReversalHurdleResponse
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.models.RequirementId.*
import xyz.block.bittycity.innie.store.DepositStore
import xyz.block.bittycity.innie.validation.ParameterIsRequired
import xyz.block.domainapi.DomainApiError.UnsupportedHurdleResultCode
import xyz.block.domainapi.Input
import xyz.block.domainapi.ResultCode
import xyz.block.domainapi.UserInteraction

class ReversalInfoCollectionController @Inject constructor(
  stateMachine: StateMachine<DepositToken, Deposit, DepositState>,
  depositStore: DepositStore,
  metricsClient: MetricsClient,
  private val currencyDisplayPreferenceClient: CurrencyDisplayPreferenceClient,
  private val walletAddressParser: WalletAddressParser
): DepositInfoCollectionController(
  pendingCollectionState = CollectingInfo,
  stateMachine = stateMachine,
  depositStore = depositStore,
  metricsClient = metricsClient,
) {

  override fun findMissingRequirements(value: Deposit): Result<List<RequirementId>> = result {
    val reversal = Result.catch { value.reversals.last() }.bind()
    listOf(
      REVERSAL_TARGET_WALLET_ADDRESS to  { reversal.targetWalletAddress == null },
      REVERSAL_USER_CONFIRMATION to { reversal.userHasConfirmed == null && reversal.targetWalletAddress != null },
    ).filter { (_, predicate) -> predicate() }
      .map { (key, _) -> key }
  }

  override fun getHurdlesForRequirementId(
    requirementId: RequirementId,
    value: Deposit,
    previousHurdles: List<UserInteraction.Hurdle<RequirementId>>
  ): Result<List<UserInteraction.Hurdle<RequirementId>>> = result {
    when (requirementId) {
      REVERSAL_TARGET_WALLET_ADDRESS -> listOf(DepositReversalHurdle.TargetWalletAddressHurdle)
      REVERSAL_USER_CONFIRMATION -> listOf(
        DepositReversalHurdle.ConfirmationHurdle(
          walletAddress = value.targetWalletAddress,
          amount = value.amount,
          fiatEquivalent = value.fiatEquivalentAmount,
          displayUnits = currencyDisplayPreferenceClient.getCurrencyDisplayPreference(
            value.customerId.id
          ).bind().bitcoinDisplayUnits
        )
      )
      else -> emptyList()
    }
  }

  override fun handleFailure(
    failure: Throwable,
    value: Deposit
  ): Result<Deposit> = failReversal(failure, value)

  override fun transition(value: Deposit): Result<Deposit> = result {
    when (value.state) {
      is CollectingInfo -> {
        stateMachine.transitionTo(value, CheckingSanctions).bind()
      }
      else -> raise(IllegalStateException("Unexpected state ${value.state}"))
    }
  }

  override fun updateValue(
    value: Deposit,
    hurdleResponse: Input.HurdleResponse<RequirementId>
  ): Result<Deposit> = result {
    when (hurdleResponse) {
      is DepositReversalHurdleResponse.TargetWalletAddressHurdleResponse -> {
        if (hurdleResponse.result != ResultCode.CLEARED) {
          raise(UnsupportedHurdleResultCode(value.customerId.id, hurdleResponse.result))
        }

        val walletAddress = hurdleResponse.walletAddress
          ?: raise(ParameterIsRequired(value.customerId, "targetWalletAddress"))

        depositStore.updateDeposit(
          value.updateCurrentReversal {
            it.copy(targetWalletAddress = walletAddressParser.parse(walletAddress).bind())
          }.bind()
        )
      }
      is DepositReversalHurdleResponse.ConfirmationHurdleResponse -> {
        depositStore.updateDeposit(
          value.updateCurrentReversal {
            it.copy(userHasConfirmed = true)
          }.bind()
        ).bind()
      }
    }
    value
  }
}
