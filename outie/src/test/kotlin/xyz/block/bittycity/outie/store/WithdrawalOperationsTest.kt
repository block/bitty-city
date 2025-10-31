package xyz.block.bittycity.outie.store

import xyz.block.bittycity.outie.jooq.JooqWithdrawalEntityOperations
import xyz.block.bittycity.outie.jooq.WithdrawalVersionMismatch
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.arbitrary.next
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import xyz.block.bittycity.outie.jooq.generated.tables.references.WITHDRAWALS
import xyz.block.bittycity.outie.models.Bitcoins
import xyz.block.bittycity.outie.models.FailureReason
import xyz.block.bittycity.outie.models.SubmittingOnChain
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import xyz.block.bittycity.outie.testing.shouldBeWithdrawal

class WithdrawalOperationsTest : BittyCityTestCase() {

  @Test
  fun `update modifies withdrawal record`() = runTest {
    // Given a withdrawal in the database
    val original = data.seedWithdrawal()

    // When updating the withdrawal
    val updated = withdrawalTransactor.transact("Update withdrawal") {
      update(
        original.copy(
          state = SubmittingOnChain,
          amount = Bitcoins(5000),
          failureReason = FailureReason.CUSTOMER_CANCELLED
        )
      )
    }.getOrThrow()

    // Then the withdrawal should be updated correctly
    dslContext.select(
      JooqWithdrawalEntityOperations.withdrawalFields
    ).from(WITHDRAWALS).fetchOne().shouldNotBeNull() should {
      it.get(WITHDRAWALS.STATE) shouldBe SubmittingOnChain.name
      it.get(WITHDRAWALS.VERSION)?.toLong() shouldBe original.version + 1
      it.get(WITHDRAWALS.SATOSHIS)?.toLong() shouldBe 5000L
      it.get(WITHDRAWALS.FAILURE_REASON) shouldBe "CUSTOMER_CANCELLED"
      // Verify immutable fields are unchanged
      it.get(WITHDRAWALS.TOKEN) shouldBe original.id.toString()
      it.get(WITHDRAWALS.CREATED_AT)?.toInstant(ZoneOffset.UTC) shouldBe original.createdAt
      it.get(WITHDRAWALS.MERCHANT_TOKEN) shouldBe original.customerId.id
    }

    // Verify the returned withdrawal matches
    updated shouldBeWithdrawal (
      original.copy(
        state = SubmittingOnChain,
        amount = Bitcoins(5000),
        failureReason = FailureReason.CUSTOMER_CANCELLED
      )
      )
  }

  @Test
  fun `update fails to modify a withdrawal that has already changed`() = runTest {
    val original = data.seedWithdrawal()

    withdrawalTransactor.transact("Update withdrawal") {
      update(
        original.copy(
          version = original.version - 1,
          state = SubmittingOnChain,
          amount = Bitcoins(5000),
          failureReason = FailureReason.CUSTOMER_CANCELLED
        )
      )
    }.shouldBeFailure().cause.shouldBeInstanceOf<WithdrawalVersionMismatch>()
  }

  @Test
  fun `update fails when withdrawal not found`() = runTest {
    // Given a non-existent withdrawal
    val nonExistentToken = Arbitrary.withdrawalToken.next()
    val nonExistentWithdrawal = data.newWithdrawal.copy(id = nonExistentToken)

    // When updating the withdrawal
    val result = withdrawalTransactor.transact("Update withdrawal") {
      update(nonExistentWithdrawal)
    }

    // Then the operation should fail
    result.shouldBeFailure()
      .cause?.message shouldBe "Withdrawal not present: ${nonExistentWithdrawal.id}"
  }

  @Test
  fun `getByTokens returns correct mapping for multiple withdrawals`() = runTest {
    // Given multiple withdrawals in the database
    val withdrawal1 = data.seedWithdrawal(id = Arbitrary.withdrawalToken.next())
    val withdrawal2 = data.seedWithdrawal(id = Arbitrary.withdrawalToken.next())
    val nonExistentToken = Arbitrary.withdrawalToken.next()

    // When fetching multiple withdrawals by their tokens
    val result = withdrawalTransactor.transactReadOnly("Get withdrawals by tokens") {
      getByTokens(listOf(withdrawal1.id, withdrawal2.id, nonExistentToken))
    }

    // Then the result should contain only the existing withdrawals
    result.shouldBeSuccess() should { withdrawals ->
      withdrawals.size shouldBe 3
      withdrawals[withdrawal1.id] shouldBeWithdrawal withdrawal1
      withdrawals[withdrawal2.id] shouldBeWithdrawal withdrawal2
      withdrawals[nonExistentToken] shouldBe null
    }
  }

  @Test
  fun `getByTokens - too many tokens`() = runTest {
    // Given multiple withdrawals in the database
    val tooManyCount = 1001
    val tooManyWithdrawalTokens = List(1001) { Arbitrary.withdrawalToken.next() }

    // When fetching multiple withdrawals by their tokens
    val result = withdrawalTransactor.transactReadOnly("Get withdrawals by tokens") {
      getByTokens(tooManyWithdrawalTokens)
    }

    // Then the result should contain only the existing withdrawals
    result.shouldBeFailure()
      .cause?.message shouldBe "Too many withdrawal tokens: $tooManyCount, exceeded limit: 1000"
  }

  @Test
  fun `getByTokens returns empty map for empty token list`() = runTest {
    // When fetching with an empty token list
    val result = withdrawalTransactor.transactReadOnly("Get withdrawals by empty token list") {
      getByTokens(emptyList())
    }

    // Then the result should be an empty map
    // Then the operation should fail
    result.shouldBeFailure()
      .cause?.message shouldBe "Withdrawal tokens not present"
  }

  @Test
  fun `getByTokens returns withdrawals with speed options when present`() = runTest {
    // Given a withdrawal with speed options
    val withdrawal = data.seedWithdrawal(withdrawalSpeed = WithdrawalSpeed.RUSH)

    // When fetching the withdrawal by token
    val result = withdrawalTransactor.transactReadOnly("Get withdrawals by tokens") {
      getByTokens(listOf(withdrawal.id))
    }

    // Then the withdrawal should include the speed option
    result.shouldBeSuccess() should { withdrawals ->
      withdrawals.size shouldBe 1
      withdrawals[withdrawal.id].shouldNotBeNull() should { retrievedWithdrawal ->
        retrievedWithdrawal.selectedSpeed.shouldNotBeNull() should { retrievedSpeedOption ->
          retrievedSpeedOption.speed shouldBe WithdrawalSpeed.RUSH
          retrievedSpeedOption.totalFee shouldBe Bitcoins(2000L)
        }
      }
    }
  }
}
