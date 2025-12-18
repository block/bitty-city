package xyz.block.bittycity.innie.fsm

import app.cash.kfsm.Transition
import app.cash.kfsm.Transitioner
import arrow.core.raise.result
import jakarta.inject.Inject
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.store.DepositOperations

class DepositTransitioner @Inject constructor(
  private val depositTransactor: Transactor<DepositOperations>,
) : Transitioner<
        DepositToken,
        Transition<DepositToken, Deposit, DepositState>,
        Deposit,
        DepositState
        >() {

  override fun persist(
    from: DepositState,
    value: Deposit,
    via: Transition<DepositToken, Deposit, DepositState>
  ): Result<Deposit> = result {
    depositTransactor.transact("Persist state transition to ${via.to}") {
      update(value.copy(state = via.to))
    }.bind()
  }
}
