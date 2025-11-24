package xyz.block.bittycity.innie.client

import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositFailureReason
import xyz.block.bittycity.innie.models.DepositState

interface MetricsClient {

  /**
   * Emits a metric that indicates a deposit transitioned from one state to another.
   *
   * @param from The source state.
   * @param to The target state.
   * @param failureReason If this transition was to a failed state, then the reason for the failure.
   */
  fun stateTransition(
    from: DepositState,
    to: DepositState,
    failureReason: DepositFailureReason? = null
  ): Result<Unit>

  /**
   * Emits a metric used to count the number of different failure reasons for deposits.
   */
  fun failureReason(
    reason: DepositFailureReason
  ): Result<Unit>

  /**
   * Histogram that keeps track of successfully deposited amounts in satoshis and fiat.
   */
  fun depositSuccessAmount(deposit: Deposit): Result<Unit>
}
