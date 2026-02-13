# AGENTS.md - LLM Contribution Guide for Bitty-City

## Project Overview

Bitty-City is a Bitcoin custodial product experience library for on-chain operations, used by Block for Square Bitcoin withdrawals. The project handles both withdrawals (`outie`) and deposits (`innie`) with a focus on Bitcoin on-chain operations.

## Technology Stack

- **Language**: Kotlin (JVM)
- **Build System**: Gradle with Kotlin DSL
- **Java Version**: JVM 11
- **Test Framework**: JUnit 5 (Jupiter) + Kotest
- **Database**: MySQL with jOOQ for type-safe SQL and Flyway for migrations
- **Key Dependencies**:
  - bitcoinj (Bitcoin operations)
  - Arrow (Functional programming)
  - kfsm (Finite state machine)
  - Guice (Dependency injection)
  - Resilience4j (Resilience patterns)
  - Moshi (JSON)
  - Kotest (Testing and assertions)
  - Mockk (Mocking)
  - TestContainers (Integration testing)

## Build & Development Commands

### Prerequisites
This project uses [Hermit](https://cashapp.github.io/hermit/) for consistent tooling. Activate it with:
 ```bash
 . ./bin/activate-hermit
 ```

### Essential Commands
 ```bash
 # Build the entire project (includes tests)
 bin/gradle build
 
 # Run tests only
 bin/gradle test
 
 # Clean build
 bin/gradle clean build
 
 # Generate state machine diagram (for outie module)
 bin/gradle :outie:generateStateMachineDiagram
 
 # Publish to Maven Central
 bin/gradle publishToMavenCentral
 ```

## Project Structure

 ```
 bitty-city/
 ├── common/             # Shared models and utilities
 ├── innie/              # Deposits module (WIP)
 └── outie/              # Withdrawals module
     ├── docs/           # Documentation including state machine diagrams
     └── src/
         ├── main/kotlin/
         └── test/kotlin/
 ```

## Code Conventions

### General Style
- **Indentation**: 2 spaces (NOT tabs)
- **Line Length**: Maximum 100 characters
- **Line Endings**: LF (Unix-style)
- **Charset**: UTF-8
- **Final Newline**: Always insert

### Kotlin-Specific
- **Imports**: No wildcard imports (use explicit imports)
- **Naming**: Follow standard Kotlin conventions
- **Null Safety**: Leverage Kotlin's null safety features
- **Functional Style**: Use Arrow for functional programming patterns
- **Type Safety**: Prefer type-safe solutions (e.g., jOOQ for SQL)
- **Expression Syntax**: Prefer expression syntax wherever possible (e.g., `when` expressions, `if` expressions)
- **Error Handling**: Never use `try/catch` - use `Result.catch` instead
- **Early Returns**: Avoid early return statements - prefer expression-based control flow
- **Elvis Operator**: Prefer elvis operators (`?:`) over `if (x == null)` expressions
- **Collections**: Prefer immutable collections over mutable collections (e.g., use `associate`, `associateWith`, `map` instead of `mutableMapOf`, `mutableListOf`)

### Testing

#### Testing Philosophy: Sociable Unit Testing

Bitty-City follows the **Sociable Unit Testing** approach for comprehensive, resilient test coverage.

**Core Principle**: Write unit tests for all classes, but let them connect through to real dependencies until hitting the system boundary.

- ✅ **Real object graphs**: Classes use their actual dependencies (services, stores, validators)
- ✅ **Real database**: Tests run against actual database with TestContainers
- ✅ **Fake external services**: Only external APIs and services are faked at system boundaries
- ✅ **Fast execution**: Still runs quickly despite using real components
- ✅ **Less brittle**: No need to update mocks when internal implementations change

**System Boundaries** (what gets faked):
- External APIs (Bitcoin RPC, blockchain explorers, etc.)
- External event systems

**Benefits over traditional mock-based testing**:
- Tests behavior, not implementation details
- Resilient to refactoring
- Real integration confidence
- Simpler test setup

#### Test Framework & Conventions

- **Framework**: JUnit 5 (Jupiter) - configured in root `build.gradle.kts`
- **Base Class**: Extend `BittyCityTestCase` for integration tests with database and dependency injection
- **Test Structure**: Use `runTest { ... }` wrapper for test body (resets fakes automatically)
- **Test App**: Access `app` property for data seeding, fakes, and test utilities
- **Test Data**: Access `app.data` for arbitrary test values (`TestRunData`)
- **Assertions**: Use Kotest matchers (`shouldBe`, `shouldBeGreaterThan`, etc.) - NOT JUnit assertions
- **Test Naming**: Test class suffix is `Test` (e.g., `BitcoinsTest.kt`)
- **Test Names**: Use backtick syntax for readable test names
- **Integration Tests**: Use TestContainers for database tests (provides real MySQL instance)
- **Mocking**: Use Mockk only for external system boundaries, not internal dependencies
- **Result Handling**: Always use `.getOrThrow()` to unwrap Arrow `Result` values in tests (never use `.shouldBeSuccess()` as it hides stack traces)
- **Test Data Generation**: Prefer `Arb<T>` generators over hard-coded values. Compose existing `Arb` generators for new types
- **Assertion Style**: For single assertions after `getOrThrow()`, chain directly instead of using `should { }` block. Use `should { }` only for multiple assertions with `assertSoftly`

#### Test Example

 ```kotlin
 // Simple unit test (no database/DI needed)
 class BitcoinsTest {
   @Test
   fun `plus operator should add two Bitcoins values`() {
     val bitcoins1 = Bitcoins(1000L)
     val bitcoins2 = Bitcoins(500L)
     val result = bitcoins1 + bitcoins2
     result.units shouldBe 1500L
   }
 }
 
 // Integration test (extends BittyCityTestCase)
 class WithdrawalServiceTest : BittyCityTestCase() {
   @Inject lateinit var withdrawalService: WithdrawalService
   
   @Test
   fun `should process withdrawal with real store and validators`() = runTest {
     val withdrawal = data.seedWithdrawal(
       amount = Bitcoins(100_000L),
       withdrawalSpeed = WithdrawalSpeed.STANDARD
     )
     
     val result = withdrawalService.process(withdrawal.id).getOrThrow()
     
     assertSoftly(result) {
       status shouldBe WithdrawalStatus.COMPLETED
       amount shouldBe Bitcoins(100_000L)
     }
   }
 }
 ```

#### Testing Best Practices

1. **Use real dependencies** - Let controllers use real stores, validators, etc.
2. **Fake at boundaries** - Only mock external APIs and services
3. **Test both paths** - Write tests for success and failure cases
4. **Use assertSoftly** - Show all assertion failures, not just the first
5. **Avoid redundant comments** - Let test names be descriptive. Never add AAA (Arrange/Act/Assert) comments
6. **Prefer brevity** - Keep tests concise and to the point
7. **Independent tests** - Each test creates its own test data
8. **Property-based test data** - Use Kotest's `Arb` generators where applicable

## Module Responsibilities

### common/
Shared models and utilities used across modules.

### outie/ (Withdrawals)
Handles Bitcoin withdrawal operations. See `outie/docs/state-machine.md` for the withdrawal state machine flow.

### innie/ (Deposits)
Handles Bitcoin deposit operations (Work in Progress).

## State Machine Documentation

Both modules use finite state machines (kfsm). The state machine diagrams can be regenerated with:
 ```bash
 bin/gradle :outie:generateStateMachineDiagram
 bin/gradle :innie:generateStateMachineDiagram
 ```

### State Naming Conventions

- **In-progress states**: Use present-participle verb phrases, e.g. `CheckingEligibility`, `CollectingReversalInfo`, `AwaitingDepositConfirmation`
- **Terminal / outcome states**: Use past-participle or adjectival forms, e.g. `Sanctioned`, `Settled`, `Reversed`, `Voided`
- Names should be as **succinct as possible** without losing context

## Publishing

The project is published to Maven Central:
- **Group**: `xyz.block.bittycity`
- **License**: Apache License 2.0
- **Repository**: https://github.com/block/bitty-city

## Contributing Guidelines

1. **Code Style**: Follow the `.editorconfig` settings (automatically enforced by IntelliJ IDEA)
2. **Testing**: All new features must include comprehensive tests
3. **Type Safety**: Prefer type-safe solutions over stringly-typed or dynamic approaches
4. **Functional Programming**: Use Arrow's functional patterns where appropriate
5. **Documentation**: Update module documentation and regenerate diagrams when modifying state machines
6. **Build Verification**: Always run `bin/gradle build` before committing to ensure tests pass
7. **Dependencies**: Check existing dependencies before adding new ones
8. **Licensing**: New runtime dependencies must be licensed with Apache 2.0, MIT, BSD, ISC, or Creative Commons Attribution (test & build dependencies can be more flexible)

## Important Notes for LLMs

- **Never add wildcard imports** - use explicit imports for all Kotlin files
- **Database changes require Flyway migrations** - never modify schema directly
- **Use Kotest for assertions**, not JUnit assertions
- **Follow the established pattern** of state machines in the outie module
- **Regenerate diagrams** after modifying FSM code
- **Check `.editorconfig`** before making formatting decisions
- **Use jOOQ** for database operations in the outie module (type-safe SQL)
- **All tests use JUnit 5** (Jupiter) - configured in root `build.gradle.kts`
