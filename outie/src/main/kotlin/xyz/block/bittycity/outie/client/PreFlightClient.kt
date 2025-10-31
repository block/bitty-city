package xyz.block.bittycity.outie.client

import xyz.block.bittycity.outie.models.Withdrawal

/**
 * A client that can be used to execute any preflight calls that might be required. The client is
 * called every time a state transition happens, as part of the post-hook function. Typically,
 * implementations will look at the state of the withdrawal to decide if a pre-flight call is
 * required.
 */
interface PreFlightClient {

  /**
   * Executes any preflight calls when a state transition happens, as part of the post-hook
   * function.
   *
   * @param value The withdrawal.
   * @return The result, typically used for logging any issues with the call. because pre-flights
   * are fire and forget.
   */
  fun doPreFlight(value: Withdrawal): Result<Unit>
}
