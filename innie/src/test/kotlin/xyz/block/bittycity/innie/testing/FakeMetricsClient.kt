package xyz.block.bittycity.innie.testing

import xyz.block.bittycity.common.testing.TestFake
import app.cash.quiver.extensions.success
import xyz.block.bittycity.innie.client.MetricsClient
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositFailureReason
import xyz.block.bittycity.innie.models.DepositReversalFailureReason
import xyz.block.bittycity.innie.models.DepositState

class FakeMetricsClient : TestFake(), MetricsClient {
  var stateTransitions by resettable { mutableListOf<StateTransitionCall>() }
  var failureReasons by resettable { mutableListOf<DepositFailureReason>() }
  var reversalFailureReasons by resettable { mutableListOf<DepositReversalFailureReason>() }
  var successAmounts by resettable { mutableListOf<Deposit>() }

  override fun stateTransition(
    from: DepositState,
    to: DepositState,
    failureReason: DepositFailureReason?
  ): Result<Unit> = Unit.success().also {
    stateTransitions.add(StateTransitionCall(from = from, to = to, failureReason = failureReason))
  }

  override fun failureReason(reason: DepositFailureReason): Result<Unit> = Unit.success().also {
    failureReasons.add(reason)
  }

  override fun reversalFailureReason(reason: DepositReversalFailureReason): Result<Unit> = Unit.success().also {
    reversalFailureReasons.add(reason)
  }

  override fun depositSuccessAmount(deposit: Deposit): Result<Unit> = Unit.success().also {
    successAmounts.add(deposit)
  }

  data class StateTransitionCall(
    val from: DepositState,
    val to: DepositState,
    val failureReason: DepositFailureReason?
  )
}
