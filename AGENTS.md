# Bitty-City

Bitcoin on-chain withdrawals (`outie`) and deposits (`innie`) library.

## Build

```bash
. ./bin/activate-hermit   # hermit tooling
bin/gradle build          # build + test
bin/gradle :outie:generateStateMachineDiagram  # after FSM changes
```

## Structure

```
common/   # Shared models
innie/    # Deposits
outie/    # Withdrawals (state machine, controllers, store)
```

## Kotlin Style

2-space indent. 100 char lines. No wildcard imports. Use expression syntax.

### ✅ Do

```kotlin
fun parse(token: String): Result<WithdrawalToken> = result {
  val uuid = Result.catch { UUID.fromString(token.removePrefix(PREFIX)) }
    .mapFailure { IllegalArgumentException("Invalid UUID:「$token」", it) }
    .bind()
  WithdrawalToken(uuid)
}
```

```kotlin
val name = customer?.name ?: "Unknown"
```

```kotlin
val fees = speeds.associate { it.speed to it.totalFee }
```

### ❌ Don't

```kotlin
// No try/catch - use Result.catch
fun parse(token: String): WithdrawalToken {
  try {
    return WithdrawalToken(UUID.fromString(token.removePrefix(PREFIX)))
  } catch (e: Exception) {
    throw IllegalArgumentException("Invalid UUID", e)
  }
}
```

```kotlin
// No early returns - use expressions
fun parse(token: String): Result<WithdrawalToken> {
  if (!token.startsWith(PREFIX)) return Result.failure(...)
  return Result.success(...)
}
```

```kotlin
// No mutable collections
val fees = mutableMapOf<WithdrawalSpeed, Bitcoins>()
speeds.forEach { fees[it.speed] = it.totalFee }
```

```kotlin
// No null checks when elvis works
val name: String
if (customer == null) {
  name = "Unknown"
} else {
  name = customer.name
}
```

## Testing

Sociable unit tests — real dependencies, fake only at system boundaries (external APIs).

### ✅ Do

```kotlin
class EligibilityControllerTest : BittyCityTestCase() {
  @Inject lateinit var subject: EligibilityController

  @Test
  fun `eligible customer transitions to holding submission`() = runTest {
    val withdrawal = data.seedWithdrawal(state = CheckingEligibility)

    subject.processInputs(withdrawal, emptyList(), Operation.EXECUTE)
      .getOrThrow()
      .shouldBeInstanceOf<ProcessingState.UserInteractions<Withdrawal, RequirementId>>()

    withdrawalWithToken(withdrawal.id).state shouldBe HoldingSubmission
  }

  @Test
  fun `should fail with LIMITED reason when withdrawal exceeds limits`() = runTest {
    val withdrawal = data.seedWithdrawal(state = HoldingSubmission)
    limitClient.nextLimitResponse =
      LimitResponse.Limited(listOf(LimitViolation.DAILY_USD_LIMIT)).success()

    subject.processInputs(withdrawal, emptyList(), Operation.EXECUTE)
      .shouldBeFailure<LimitWouldBeExceeded>()

    withdrawalWithToken(withdrawal.id) should {
      it.state shouldBe Failed
      it.failureReason shouldBe FailureReason.LIMITED
    }
  }
}
```

### ❌ Don't

```kotlin
// No JUnit assertions
assertEquals(HoldingSubmission, result.state)
assertTrue(result.isSuccess)

// No .shouldBeSuccess() - hides stack traces
service.process(id).shouldBeSuccess()

// No mocking internal dependencies
val mockStore = mockk<WithdrawalStore>()
every { mockStore.findByToken(any()) } returns ...

// No AAA comments
// Arrange
val withdrawal = ...
// Act
val result = ...
// Assert
result shouldBe ...
```

### Key patterns

- Extend `BittyCityTestCase` for integration tests
- Use `runTest { ... }` — it resets fakes automatically
- Seed data via `data.seedWithdrawal(...)`
- Use `.getOrThrow()` to unwrap `Result` values
- Use Kotest: `shouldBe`, `shouldBeInstanceOf`, `shouldBeFailure`
- Use `should { }` with `assertSoftly` only for multiple assertions
- Use `Arb<T>` generators over hard-coded values

## State Machines (kfsm)

- In-progress states: present participle — `CheckingEligibility`, `AwaitingDepositConfirmation`
- Terminal states: past participle — `Settled`, `Reversed`, `Voided`
- Regenerate diagrams after changes

## Database

- Schema changes require Flyway migrations — never modify schema directly
- Use the `Transactor<WithdrawalOperations>` pattern for database access
- jOOQ for type-safe SQL in the `outie-jooq-provider` module
