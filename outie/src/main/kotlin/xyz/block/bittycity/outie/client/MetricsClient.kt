package xyz.block.bittycity.outie.client

import kotlin.time.Duration
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState

interface MetricsClient {

  /**
   * Emits a metric that indicates a withdrawal transitioned from one state to another.
   *
   * @param from The source state.
   * @param to The target state.
   * @param failureReason If this transition was to a failed state, then the reason for the failure.
   */
  fun stateTransition(
    from: WithdrawalState,
    to: WithdrawalState,
    failureReason: FailureReason? = null
  ): Result<Unit>

  /**
   * Emits a metric used to count the number of different failure reasons for withdrawals.
   */
  fun failureReason(
    reason: FailureReason
  ): Result<Unit>

  /**
   * Histogram that keeps track of successfully withdrawn amounts in satoshis and fiat.
   */
  fun withdrawalSuccessAmount(withdrawal: Withdrawal): Result<Unit>

  /**
   * Timer that records how long a controller spent processing a withdrawal in one state.
   *
   * @param state The state the withdrawal was in when processing started.
   * @param duration Wall-clock time spent in the controller's processInputs call.
   * @param succeeded Whether processInputs returned a successful result.
   */
  fun stateProcessingDuration(
    state: WithdrawalState,
    duration: Duration,
    succeeded: Boolean
  ): Result<Unit> = Result.success(Unit)
}
