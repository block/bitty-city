package xyz.block.bittycity.outie.store

import xyz.block.bittycity.outie.jooq.JooqWithdrawalEntityOperations
import xyz.block.bittycity.outie.jooq.generated.tables.references.WITHDRAWALS
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.outie.models.SubmittingOnChain
import xyz.block.bittycity.outie.testing.Arbitrary
import xyz.block.bittycity.outie.testing.BittyCityTestCase
import xyz.block.bittycity.outie.testing.shouldBeWithdrawal
import xyz.block.bittycity.outie.testing.singleElementShould
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class WithdrawalStoreTest : BittyCityTestCase() {

  @Inject lateinit var withdrawalStore: WithdrawalStore

  @Test
  fun `create new withdrawal success`() = runTest {
    withdrawalStore.insertWithdrawal(
      withdrawalToken = data.withdrawalToken,
      customerId = data.customerToken,
      sourceBalanceToken = data.balanceId,
      state = data.newWithdrawal.state,
      source = data.newWithdrawal.source,
    ).getOrThrow()

    dslContext.select(
      JooqWithdrawalEntityOperations.withdrawalFields
    ).from(WITHDRAWALS).fetchOne().shouldNotBeNull() should {
      it.get(WITHDRAWALS.TOKEN) shouldBe data.withdrawalToken.toString()
      it.get(WITHDRAWALS.MERCHANT_TOKEN) shouldBe data.customerToken.toString()
      it.get(WITHDRAWALS.SOURCE_BALANCE_TOKEN) shouldBe data.balanceId.id
      it.get(WITHDRAWALS.STATE) shouldBe data.newWithdrawal.state.name
      it.get(WITHDRAWALS.SOURCE) shouldBe data.newWithdrawal.source
    }
  }

  @Test
  fun `findByToken - withdrawal found`() = runTest {
    // Given a withdrawal in the database
    val withdrawal = data.seedWithdrawal()

    // When looking up the withdrawal by token
    val found = withdrawalStore.findWithdrawalByToken(withdrawal.id).getOrThrow()

    // Then the withdrawal should be found with matching data
    found.shouldBeWithdrawal(withdrawal)
  }

  @Test
  fun `findByToken - withdrawal not found`() = runTest {
    withdrawalStore.findWithdrawalByToken(Arbitrary.withdrawalToken.next()).getOrThrow() shouldBe
      null
  }

  @Test
  fun `getByToken - withdrawal found`() = runTest {
    // Given a withdrawal in the database
    val withdrawal = data.seedWithdrawal()

    // When looking up the withdrawal by token
    val found = withdrawalStore.getWithdrawalByToken(withdrawal.id).getOrThrow()

    // Then the withdrawal should be found with matching data
    found shouldBeWithdrawal (withdrawal)
  }

  @Test
  fun `getByToken - withdrawal not found`() = runTest {
    withdrawalStore.getWithdrawalByToken(Arbitrary.withdrawalToken.next())
      .shouldBeFailure()
  }

  @Test
  fun `getByTokens - 2 found, 1 not`() = runTest {
    // Given multiple withdrawals in the database
    val withdrawal1 = data.seedWithdrawal(id = Arbitrary.withdrawalToken.next())
    val withdrawal2 = data.seedWithdrawal(id = Arbitrary.withdrawalToken.next())
    val nonExistentToken = Arbitrary.withdrawalToken.next()

    // When fetching multiple withdrawals by their tokens
    val result = withdrawalStore.getWithdrawalsByTokens(
      listOf(withdrawal1.id, withdrawal2.id, nonExistentToken)
    )

    // Then the result should contain only the existing withdrawals
    result.getOrThrow() should { withdrawals ->
      withdrawals.size shouldBe 3
      withdrawals[withdrawal1.id] shouldBeWithdrawal withdrawal1
      withdrawals[withdrawal2.id] shouldBeWithdrawal withdrawal2
      withdrawals[nonExistentToken] shouldBe null
    }
  }

  @Test
  fun `search returns empty list when no withdrawals exist`() = runTest {
    val customerId = CustomerId("test-customer")
    val results = withdrawalStore.search(customerId = customerId).getOrThrow()
    results.shouldBeEmpty()
  }

  @Test
  fun `search returns only withdrawals for specified customer`() = runTest {
    // Create withdrawals for two different customers
    val customer1 = CustomerId("customer-1")
    val customer2 = CustomerId("customer-2")

    val saved = withdrawalStore.insertWithdrawal(
      withdrawalToken = data.withdrawalToken,
      customerId = customer1,
      sourceBalanceToken = data.balanceId,
      state = CollectingInfo,
      source = "test"
    ).getOrThrow()

    withdrawalStore.insertWithdrawal(
      withdrawalToken = Arbitrary.withdrawalToken.next(),
      customerId = customer2,
      sourceBalanceToken = data.balanceId,
      state = CollectingInfo,
      source = "test"
    ).getOrThrow()

    // Search for customer1's withdrawals
    val results = withdrawalStore.search(customerId = customer1).getOrThrow()
    results.singleElementShould {
      it.shouldBeWithdrawal(saved)
    }
  }

  @Test
  fun `search filters by time range correctly`() = runTest {
    val customerId = CustomerId("test-customer")

    // Create withdrawals at different times
    val saved = withdrawalStore.insertWithdrawal(
      withdrawalToken = data.withdrawalToken,
      customerId = customerId,
      sourceBalanceToken = data.balanceId,
      state = CollectingInfo,
      source = "test",
      createdAt = clock.instant()
    ).getOrThrow()

    clock.advance(Duration.ofSeconds(5))

    // Create second withdrawal 5 seconds later
    withdrawalStore.insertWithdrawal(
      withdrawalToken = Arbitrary.withdrawalToken.next(),
      customerId = customerId,
      sourceBalanceToken = data.balanceId,
      state = CollectingInfo,
      source = "test",
      createdAt = clock.instant()
    ).getOrThrow()

    // Search with time range that spans multiple seconds to account for MySQL TIMESTAMP precision
    val results = withdrawalStore.search(
      customerId = customerId,
      from = clock.instant().minus(6, ChronoUnit.SECONDS),
      to = clock.instant().minus(4, ChronoUnit.SECONDS),
    ).getOrThrow()

    results.singleElementShould {
      it.shouldBeWithdrawal(saved)
    }
  }

  @Test
  fun `search filters by amount range correctly`() = runTest {
    val customerId = CustomerId("test-customer")

    // Create withdrawals with different amounts
    withdrawalStore.insertWithdrawal(
      withdrawalToken = data.withdrawalToken,
      customerId = customerId,
      sourceBalanceToken = data.balanceId,
      state = CollectingInfo,
      source = "test",
      amount = Bitcoins(1000),
      exchangeRate = data.exchangeRate
    ).getOrThrow()

    val saved = withdrawalStore.insertWithdrawal(
      withdrawalToken = Arbitrary.withdrawalToken.next(),
      customerId = customerId,
      sourceBalanceToken = data.balanceId,
      state = CollectingInfo,
      source = "test",
      amount = Bitcoins(2000),
      exchangeRate = data.exchangeRate
    ).getOrThrow()

    // Search with amount range
    val results = withdrawalStore.search(
      customerId = customerId,
      minAmount = Bitcoins(1500),
      maxAmount = Bitcoins(2500)
    ).getOrThrow()

    results.singleElementShould {
      it.shouldBeWithdrawal(saved)
    }
  }

  @Test
  fun `search filters by state correctly`() = runTest {
    val customerId = CustomerId("test-customer")

    // Create withdrawals in different states
    val saved = withdrawalStore.insertWithdrawal(
      withdrawalToken = data.withdrawalToken,
      customerId = customerId,
      sourceBalanceToken = data.balanceId,
      state = CollectingInfo,
      source = "test"
    ).getOrThrow()

    withdrawalStore.insertWithdrawal(
      withdrawalToken = Arbitrary.withdrawalToken.next(),
      customerId = customerId,
      sourceBalanceToken = data.balanceId,
      state = SubmittingOnChain,
      source = "test"
    ).getOrThrow()

    // Search for specific states
    val results = withdrawalStore.search(
      customerId = customerId,
      states = setOf(CollectingInfo)
    ).getOrThrow()

    results.singleElementShould {
      it.shouldBeWithdrawal(saved)
    }
  }

  @Test
  fun `search filters by destination address correctly`() = runTest {
    val customerId = CustomerId("test-customer")
    val address1 = Arbitrary.walletAddress.next()
    val address2 = Arbitrary.walletAddress.next()

    // Create withdrawals with different destination addresses
    val saved = withdrawalStore.insertWithdrawal(
      withdrawalToken = data.withdrawalToken,
      customerId = customerId,
      sourceBalanceToken = data.balanceId,
      state = CollectingInfo,
      source = "test",
      targetWalletAddress = address1
    ).getOrThrow()

    withdrawalStore.insertWithdrawal(
      withdrawalToken = Arbitrary.withdrawalToken.next(),
      customerId = customerId,
      sourceBalanceToken = data.balanceId,
      state = CollectingInfo,
      source = "test",
      targetWalletAddress = address2
    ).getOrThrow()

    // Search for specific destination address
    val results = withdrawalStore.search(
      customerId = customerId,
      destinationAddress = address1.toString()
    ).getOrThrow()

    results.singleElementShould {
      it.shouldBeWithdrawal(saved)
    }
  }

  @Test
  fun `search combines multiple filters correctly`() = runTest {
    val customerId = CustomerId("test-customer")
    val address = Arbitrary.walletAddress.next()
    val address2 = Arbitrary.walletAddress.next()
    val now = Instant.now()

    // Create withdrawal that matches all filters
    val saved = withdrawalStore.insertWithdrawal(
      withdrawalToken = data.withdrawalToken,
      customerId = customerId,
      sourceBalanceToken = data.balanceId,
      state = CollectingInfo,
      source = "test",
      amount = Bitcoins(1500),
      targetWalletAddress = address,
      exchangeRate = data.exchangeRate
    ).getOrThrow()

    // Create withdrawal that doesn't match filters
    withdrawalStore.insertWithdrawal(
      withdrawalToken = Arbitrary.withdrawalToken.next(),
      customerId = customerId,
      sourceBalanceToken = data.balanceId,
      state = SubmittingOnChain,
      source = "test",
      amount = Bitcoins(2500),
      targetWalletAddress = address2,
      exchangeRate = data.exchangeRate
    ).getOrThrow()

    // Search with multiple filters
    val results = withdrawalStore.search(
      customerId = customerId,
      from = now.minus(1, ChronoUnit.HOURS),
      to = now.plus(1, ChronoUnit.HOURS),
      minAmount = Bitcoins(1000),
      maxAmount = Bitcoins(2000),
      states = setOf(CollectingInfo),
      destinationAddress = address.toString()
    ).getOrThrow()

    results.singleElementShould {
      it.shouldBeWithdrawal(saved)
    }
  }
}
