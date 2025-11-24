package xyz.block.bittycity.innie.testing

import arrow.core.raise.result
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.innie.store.DepositOperations

/**
 * In-memory fake transactor for testing that doesn't provide real transaction semantics
 * but allows operations to be executed.
 */
class FakeDepositTransactor(
  private val operations: DepositOperations
) : Transactor<DepositOperations> {

  override fun <T> transact(comment: String, block: DepositOperations.() -> Result<T>): Result<T> =
    result {
      operations.block().bind()
    }

  override fun <T> transactReadOnly(comment: String, block: DepositOperations.() -> Result<T>): Result<T> =
    result {
      operations.block().bind()
    }
}
