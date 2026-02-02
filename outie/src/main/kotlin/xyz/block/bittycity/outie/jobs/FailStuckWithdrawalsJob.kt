package xyz.block.bittycity.outie.jobs

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject
import jakarta.inject.Named
import kotlin.time.Duration.Companion.minutes
import xyz.block.bittycity.outie.fsm.WithdrawalEffect

class FailStuckWithdrawalsJob @Inject constructor(
  override val stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState, WithdrawalEffect>,
  private val withdrawalStore: WithdrawalStore,
  private val metricsClient: MetricsClient,
  @param:Named("withdrawal.stuck_after_minutes") private val stuckAfterMinutes: Long,
) {

  val logger: KLogger = KotlinLogging.logger {}

  /**
   * Finds and fails any stuck withdrawals.
   *
   * @param logOnly If true, does not automatically fail stuck withdrawals, just logs them.
   */
  fun execute(logOnly: Boolean): Result<Unit> = result {
    withdrawalStore.findStuckWithdrawals(stuckAfterMinutes.minutes, false).bind().forEach { withdrawal ->
      logger.info {
        "Found stuck withdrawal ${withdrawal.id} in state ${withdrawal.state} " +
          "(last updated on ${withdrawal.updatedAt})"
      }
      if (!logOnly) {
        withdrawal.fail(FailureReason.CUSTOMER_ABANDONED, metricsClient).onFailure {
          logger.warn(it) { "There was a problem failing stuck withdrawal ${withdrawal.id}" }
        }.bind()
      }
    }
  }
}
