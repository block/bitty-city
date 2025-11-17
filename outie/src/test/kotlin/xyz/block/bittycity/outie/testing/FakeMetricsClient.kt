package xyz.block.bittycity.outie.testing

import xyz.block.bittycity.common.testing.TestFake

import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState

class FakeMetricsClient :
  TestFake(),
  MetricsClient {
  override fun stateTransition(
    from: WithdrawalState,
    to: WithdrawalState,
    failureReason: FailureReason?
  ): Result<Unit> = Result.success(Unit)

  override fun failureReason(reason: FailureReason): Result<Unit> = Result.success(Unit)
  override fun withdrawalSuccessAmount(withdrawal: Withdrawal): Result<Unit>  = Result.success(Unit)
}
