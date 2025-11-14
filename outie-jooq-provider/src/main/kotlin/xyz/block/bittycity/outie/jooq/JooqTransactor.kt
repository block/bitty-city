package xyz.block.bittycity.outie.jooq

import app.cash.quiver.extensions.catch
import app.cash.quiver.extensions.mapFailure
import org.jooq.DSLContext
import org.jooq.impl.DSL
import xyz.block.bittycity.common.store.Operations
import xyz.block.bittycity.common.store.Transactor

class JooqTransactor<O : Operations>(
  private val dslContext: DSLContext,
  private val operationsFactory: (DSLContext) -> O
) : Transactor<O> {

  override fun <T> transact(comment: String, block: O.() -> Result<T>): Result<T> =
    Result.catch {
      dslContext.transactionResult { config ->
        block(operationsFactory(DSL.using(config))).getOrThrow()
      }
    }.mapFailure(::unwrapPlainRuntimeException)

  // Not optimised for read-only operations
  override fun <T> transactReadOnly(comment: String, block: O.() -> Result<T>): Result<T> =
    transact(comment, block)

  private fun unwrapPlainRuntimeException(e: Throwable): Throwable =
    if (e.javaClass == RuntimeException::class.java) e.cause ?: e else e
}
