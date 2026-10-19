package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.StateMachine
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.arbitrary.next
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import org.junit.jupiter.api.Test
import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.models.CheckingRisk
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import xyz.block.domainapi.Input
import xyz.block.domainapi.ProcessingState
import xyz.block.domainapi.util.Controller
import xyz.block.domainapi.util.Operation

class TimedControllerTest : BittyCityTestCase() {
  @Test
  fun `reports duration with succeeded true and returns the delegate result unchanged`() = runTest {
    val withdrawal = Arbitrary.withdrawal.next().copy(state = CheckingRisk)
    val timeSource = TestTimeSource()
    val result: Result<ProcessingState<Withdrawal, RequirementId>> =
      Result.success(ProcessingState.Waiting(withdrawal))
    val stub = StubController(withdrawalStateMachine, timeSource, 250.milliseconds, result)
    val subject = TimedController(stub, metricsClient, timeSource)

    val actual = subject.processInputs(withdrawal, emptyList(), Operation.EXECUTE, null)

    actual shouldBe result
    actual.getOrThrow() shouldBeSameInstanceAs result.getOrThrow()

    metricsClient.stateProcessingDurations.single() should {
      it.state shouldBe CheckingRisk
      it.duration shouldBe 250.milliseconds
      it.succeeded shouldBe true
    }
  }

  @Test
  fun `reports succeeded false when the delegate fails and preserves the failure`() = runTest {
    val withdrawal = Arbitrary.withdrawal.next().copy(state = CheckingRisk)
    val timeSource = TestTimeSource()
    val failure = IllegalStateException("boom")
    val result: Result<ProcessingState<Withdrawal, RequirementId>> = Result.failure(failure)
    val stub = StubController(withdrawalStateMachine, timeSource, 250.milliseconds, result)
    val subject = TimedController(stub, metricsClient, timeSource)

    val actual = subject.processInputs(withdrawal, emptyList(), Operation.EXECUTE, null)

    actual.shouldBeFailure<IllegalStateException>()
    actual.exceptionOrNull() shouldBeSameInstanceAs failure
    metricsClient.stateProcessingDurations.single() should {
      it.state shouldBe CheckingRisk
      it.succeeded shouldBe false
    }
  }

  @Test
  fun `metric failure result is swallowed`() = runTest {
    val withdrawal = Arbitrary.withdrawal.next().copy(state = CheckingRisk)
    val timeSource = TestTimeSource()
    val result: Result<ProcessingState<Withdrawal, RequirementId>> =
      Result.success(ProcessingState.Waiting(withdrawal))
    val stub = StubController(withdrawalStateMachine, timeSource, 250.milliseconds, result)
    metricsClient.nextStateProcessingDurationResult =
      Result.failure(RuntimeException("metrics down"))
    val subject = TimedController(stub, metricsClient, timeSource)

    val actual = subject.processInputs(withdrawal, emptyList(), Operation.EXECUTE, null)

    actual shouldBe result
    actual.getOrThrow() shouldBeSameInstanceAs result.getOrThrow()
  }

  @Test
  fun `metric exception is swallowed`() = runTest {
    val withdrawal = Arbitrary.withdrawal.next().copy(state = CheckingRisk)
    val timeSource = TestTimeSource()
    val result: Result<ProcessingState<Withdrawal, RequirementId>> =
      Result.success(ProcessingState.Waiting(withdrawal))
    val stub = StubController(withdrawalStateMachine, timeSource, 250.milliseconds, result)
    val throwingMetricsClient = object : MetricsClient {
      override fun stateTransition(
        from: WithdrawalState,
        to: WithdrawalState,
        failureReason: FailureReason?
      ): Result<Unit> = Result.success(Unit)

      override fun failureReason(reason: FailureReason): Result<Unit> = Result.success(Unit)

      override fun withdrawalSuccessAmount(withdrawal: Withdrawal): Result<Unit> =
        Result.success(Unit)

      override fun stateProcessingDuration(
        state: WithdrawalState,
        duration: Duration,
        succeeded: Boolean
      ): Result<Unit> = throw RuntimeException("metrics down")
    }
    val subject = TimedController(stub, throwingMetricsClient, timeSource)

    val actual = subject.processInputs(withdrawal, emptyList(), Operation.EXECUTE, null)

    actual shouldBe result
    actual.getOrThrow() shouldBeSameInstanceAs result.getOrThrow()
  }
}

private class StubController(
  override val stateMachine: StateMachine<WithdrawalToken, Withdrawal, WithdrawalState>,
  private val timeSource: TestTimeSource,
  private val advanceBy: Duration,
  private val result: Result<ProcessingState<Withdrawal, RequirementId>>,
) : Controller<WithdrawalToken, WithdrawalState, Withdrawal, RequirementId> {
  override fun processInputs(
    value: Withdrawal,
    inputs: List<Input<RequirementId>>,
    operation: Operation,
    hurdleGroupId: String?
  ): Result<ProcessingState<Withdrawal, RequirementId>> = result.also { timeSource += advanceBy }

  override fun handleFailure(failure: Throwable, value: Withdrawal): Result<Withdrawal> =
    Result.failure(failure)
}
