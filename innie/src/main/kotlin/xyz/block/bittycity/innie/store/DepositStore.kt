package xyz.block.bittycity.innie.store

import arrow.core.raise.result
import jakarta.inject.Inject
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositToken

class DepositStore @Inject constructor(
  private val depositTransactor: Transactor<DepositOperations>
) {
  fun getDepositByToken(token: DepositToken): Result<Deposit> =
    depositTransactor.transactReadOnly("Find deposit by token") {
      getByToken(token)
    }

  fun insertDeposit(deposit: Deposit): Result<Deposit> = depositTransactor.transact("Insert deposit") {
    result {
      insert(deposit).bind().also {
        insertDepositEvent(deposit.id, null, deposit.state, it).bind()
      }
    }
  }

  fun updateDeposit(deposit: Deposit): Result<Deposit> =
    depositTransactor.transact("Update deposit") {
      update(deposit)
    }
}
