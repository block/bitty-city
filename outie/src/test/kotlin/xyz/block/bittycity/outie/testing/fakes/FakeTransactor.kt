package xyz.block.bittycity.outie.testing.fakes

import xyz.block.bittycity.common.store.Operations
import xyz.block.bittycity.common.store.Transactor

class FakeTransactor<O : Operations>(
  private val operations: O
) : Transactor<O> {
  override fun <T> transact(comment: String, block: O.() -> Result<T>): Result<T> =
    operations.block()

  override fun <T> transactReadOnly(comment: String, block: O.() -> Result<T>): Result<T> =
    operations.block()
}
