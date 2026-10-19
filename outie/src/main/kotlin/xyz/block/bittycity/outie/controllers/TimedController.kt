package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.StateMachine
import app.cash.quiver.extensions.catch
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.TimeSource
import kotlin.time.measureTimedValue
import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Controller
import xyz.block.domainapi.util.Operation

/**
 * Decorates a withdrawal [Controller] so each processInputs call is timed and reported through
 * [MetricsClient.stateProcessingDuration]. Metric failures are logged and never alter the result.
 */
class TimedController(
  private val delegate: Controller<WithdrawalToken, WithdrawalState, Withdrawal, RequirementId>,
  private val metricsClient: MetricsClient,
  private val timeSource: TimeSource = TimeSource.Monotonic,
) : Controller<WithdrawalToken, WithdrawalState, Withdrawal, RequirementId> {
  private val logger: KLogger = KotlinLogging.logger {}

  override val stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState>
    get() = delegate.stateMachine

  override fun processInputs(
    value: Withdrawal,
    inputs: List<Input<RequirementId>>,
    operation: Operation,
    hurdleGroupId: String?
  ): Result<ProcessingState<Withdrawal, RequirementId>> =
    timeSource.measureTimedValue {
      delegate.processInputs(value, inputs, operation, hurdleGroupId)
    }.let { (result, duration) ->
      result.also {
        Result.catch {
          metricsClient.stateProcessingDuration(value.state, duration, result.isSuccess)
            .getOrThrow()
        }.recover {
          logger.warn(it) {
            "Failure to publish state processing duration metric for state ${value.state}"
          }
        }
      }
    }

  override fun handleFailure(failure: Throwable, value: Withdrawal): Result<Withdrawal> =
    delegate.handleFailure(failure, value)

  override fun handleCancelled(
    value: Withdrawal,
    requirementResults: List<Input<RequirementId>>
  ): Result<ProcessingState<Withdrawal, RequirementId>?> =
    delegate.handleCancelled(value, requirementResults)
}
