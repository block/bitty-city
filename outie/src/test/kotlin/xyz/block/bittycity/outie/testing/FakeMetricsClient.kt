package xyz.block.bittycity.outie.testing

import kotlin.time.Duration
import xyz.block.bittycity.common.testing.TestFake
import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState

class FakeMetricsClient :
  TestFake(),
  MetricsClient {
  data class StateProcessingDuration(
    val state: WithdrawalState,
    val duration: Duration,
    val succeeded: Boolean
  )

  val stateProcessingDurations: MutableList<StateProcessingDuration> by resettable {
    mutableListOf()
  }
  var nextStateProcessingDurationResult: Result<Unit> by resettable { Result.success(Unit) }

  override fun stateProcessingDuration(
    state: WithdrawalState,
    duration: Duration,
    succeeded: Boolean
  ): Result<Unit> = nextStateProcessingDurationResult.also {
    stateProcessingDurations.add(StateProcessingDuration(state, duration, succeeded))
  }

  override fun stateTransition(
    from: WithdrawalState,
    to: WithdrawalState,
    failureReason: FailureReason?
  ): Result<Unit> = Result.success(Unit)

  override fun failureReason(reason: FailureReason): Result<Unit> = Result.success(Unit)
  override fun withdrawalSuccessAmount(withdrawal: Withdrawal): Result<Unit>  = Result.success(Unit)
}
