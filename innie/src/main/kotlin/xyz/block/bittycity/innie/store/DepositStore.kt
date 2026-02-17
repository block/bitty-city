package xyz.block.bittycity.innie.store

import arrow.core.raise.result
import jakarta.inject.Inject
import org.bitcoinj.base.Address
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import java.time.Instant

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

  @Suppress("LongParameterList")
  fun search(
    customerId: CustomerId?,
    from: Instant? = null,
    to: Instant? = null,
    minAmount: Bitcoins? = null,
    maxAmount: Bitcoins? = null,
    states: Set<DepositState> = setOf(),
    targetWalletAddress: Address? = null,
    paymentToken: String? = null
  ): Result<List<Deposit>> = depositTransactor.transactReadOnly("Search deposits") {
    searchDeposits(
      customerId = customerId,
      from = from,
      to = to,
      minAmount = minAmount,
      maxAmount = maxAmount,
      states = states,
      targetWalletAddress = targetWalletAddress,
      paymentToken = paymentToken
    )
  }
}
