package xyz.block.bittycity.outie.client

import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalToken

/**
 * Publish events when withdrawals change state.
 */
interface EventClient {
  /**
   * Publish a withdrawal event.
   *
   * @param event The withdrawal event.
   */
  fun publish(event: WithdrawalEvent): Result<Unit>
}

data class WithdrawalEvent(
  val withdrawalToken: WithdrawalToken,
  val newWithdrawal: Withdrawal,
  val oldWithdrawal: Withdrawal?,
  val eventType: EventType
) {
  enum class EventType {
    CREATE,
    UPDATE,
    DELETE,
  }
}
