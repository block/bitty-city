package xyz.block.bittycity.outie.client

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
}
