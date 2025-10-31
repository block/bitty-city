package xyz.block.bittycity.outie.store

interface Operations

interface Transactor<O : Operations> {
  fun <T> transact(comment: String, block: O.() -> Result<T>): Result<T>
  fun <T> transactReadOnly(comment: String, block: O.() -> Result<T>): Result<T>
}

