package xyz.block.bittycity.common.client

/**
 * A client that can be used to execute any preflight calls that might be required. The client is
 * called every time a state transition happens, as part of the post-hook function. Typically,
 * implementations will look at the state of the value to decide if a pre-flight call is required.
 *
 * @param T The type of value being processed (e.g., Withdrawal, Deposit).
 */
interface PreFlightClient<T> {

  /**
   * Executes any preflight calls when a state transition happens, as part of the post-hook
   * function.
   *
   * @param value The value being transitioned.
   * @return The result, typically used for logging any issues with the call, because pre-flights
   * are fire and forget.
   */
  fun doPreFlight(value: T): Result<Unit>
}
