package xyz.block.bittycity.innie.testing

import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.innie.store.ResponseOperations

/**
 * Simple fake transactor that delegates directly to the operations without actual transaction management.
 */
class FakeResponseTransactor(
  private val operations: ResponseOperations
) : Transactor<ResponseOperations> {

  override fun <T> transact(comment: String, block: ResponseOperations.() -> Result<T>): Result<T> {
    return operations.block()
  }

  override fun <T> transactReadOnly(comment: String, block: ResponseOperations.() -> Result<T>): Result<T> {
    return operations.block()
  }
}
