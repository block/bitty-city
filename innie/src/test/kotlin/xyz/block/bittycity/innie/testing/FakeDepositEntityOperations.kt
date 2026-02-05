package xyz.block.bittycity.innie.testing

import arrow.core.raise.result
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositReversal
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.store.DepositEntityOperations
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory fake implementation of DepositEntityOperations for testing.
 */
class FakeDepositEntityOperations : DepositEntityOperations {
  private val deposits = ConcurrentHashMap<DepositToken, Deposit>()
  private val reversals = ConcurrentHashMap<DepositToken, MutableList<DepositReversal>>()

  override fun insert(deposit: Deposit): Result<Deposit> = result {
    val existing = deposits.putIfAbsent(deposit.id, deposit.copy(version = 1))
    if (existing != null) {
      raise(IllegalStateException("Deposit with token ${deposit.id} already exists"))
    }
    deposit.copy(version = 1)
  }

  override fun getByToken(token: DepositToken): Result<Deposit> = result {
    deposits[token] ?: raise(IllegalStateException("Deposit with token $token not found"))
  }

  override fun findByToken(token: DepositToken): Result<Deposit?> = result {
    deposits[token]
  }

  override fun getByTokens(tokens: List<DepositToken>): Result<Map<DepositToken, Deposit?>> = result {
    tokens.associateWith { deposits[it] }
  }

  override fun update(deposit: Deposit): Result<Deposit> = result {
    val existing = deposits[deposit.id]
      ?: raise(IllegalStateException("Deposit with token ${deposit.id} not found"))

    // Optimistic locking check
    if (existing.version != deposit.version) {
      raise(
        IllegalStateException(
          "Deposit version mismatch: expected ${deposit.version}, found ${existing.version}"
        )
      )
    }

    val updated = deposit.copy(
      version = (deposit.version ?: 0) + 1,
      reversals = reversals[deposit.id]?.toList() ?: deposit.reversals
    )
    deposits[deposit.id] = updated
    updated
  }

  override fun addReversal(id: DepositToken, reversal: DepositReversal): Result<DepositReversal> = result {
    val deposit = deposits[id]
      ?: raise(IllegalStateException("Deposit with token $id not found"))

    val reversalList = reversals.getOrPut(id) { mutableListOf() }
    val versionedReversal = reversal.copy(version = reversalList.size.toLong() + 1)
    reversalList.add(versionedReversal)

    // Update the deposit's reversals list
    deposits[id] = deposit.copy(reversals = reversalList.toList())

    versionedReversal
  }

  override fun getLatestReversal(id: DepositToken): Result<DepositReversal?> = result {
    reversals[id]?.lastOrNull()
  }

  fun clear() {
    deposits.clear()
    reversals.clear()
  }
}
