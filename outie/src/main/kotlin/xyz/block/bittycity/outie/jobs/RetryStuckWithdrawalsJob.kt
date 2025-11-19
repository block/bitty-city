package xyz.block.bittycity.outie.jobs

import app.cash.kfsm.StateMachine
import arrow.core.raise.result
import xyz.block.bittycity.outie.api.WithdrawalDomainController
import xyz.block.bittycity.outie.controllers.WithdrawalTransitioner
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.WithdrawalStore
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject
import jakarta.inject.Named
import xyz.block.domainapi.util.Operation
import kotlin.time.Duration.Companion.minutes

class RetryStuckWithdrawalsJob @Inject constructor(
  override val stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState>,
  private val withdrawalStore: WithdrawalStore,
  private val domainController: WithdrawalDomainController,
  @param:Named("withdrawal.retryable_stuck_after_minutes") private val stuckAfterMinutes: Long,
) : WithdrawalTransitioner {

  override val logger: KLogger = KotlinLogging.logger {}

  /**
   * Finds and retries any stuck withdrawals.
   *
   * @param logOnly If true, does not automatically retry stuck withdrawals, just logs them.
   */
  fun execute(logOnly: Boolean): Result<Unit> = result {
    withdrawalStore.findStuckWithdrawals(stuckAfterMinutes.minutes, true).bind().forEach { withdrawal ->
      logger.info {
        "Found retryable stuck withdrawal ${withdrawal.id} in state ${withdrawal.state} " +
          "(last updated on ${withdrawal.updatedAt})"
      }
      if (!logOnly) {
        domainController.execute(withdrawal, emptyList(), Operation.EXECUTE).bind()
      }
    }
  }
}
