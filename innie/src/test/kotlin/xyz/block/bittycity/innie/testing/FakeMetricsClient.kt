package xyz.block.bittycity.innie.testing

import xyz.block.bittycity.common.testing.TestFake
import app.cash.quiver.extensions.success
import xyz.block.bittycity.innie.client.MetricsClient
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositFailureReason
import xyz.block.bittycity.innie.models.DepositReversalFailureReason
import xyz.block.bittycity.innie.models.DepositState

class FakeMetricsClient : TestFake(), MetricsClient {

  override fun stateTransition(
    from: DepositState,
    to: DepositState,
    failureReason: DepositFailureReason?
  ): Result<Unit> = Unit.success()

  override fun failureReason(reason: DepositFailureReason): Result<Unit> = Unit.success()

  override fun reversalFailureReason(reason: DepositReversalFailureReason): Result<Unit> = Unit.success()

  override fun depositSuccessAmount(deposit: Deposit): Result<Unit> = Unit.success()
}
