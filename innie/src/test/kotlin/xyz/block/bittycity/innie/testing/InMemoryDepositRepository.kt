package xyz.block.bittycity.innie.testing

import app.cash.kfsm.v2.OutboxMessage
import app.cash.kfsm.v2.Repository
import jakarta.inject.Inject
import xyz.block.bittycity.common.testing.TestFake
import xyz.block.bittycity.innie.fsm.DepositEffect
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken

/**
 * Repository implementation that delegates storage to FakeDepositOperations,
 * ensuring the state machine and DepositStore share the same backing data.
 */
class InMemoryDepositRepository @Inject constructor(
  private val outbox: InMemoryOutbox<DepositToken, DepositEffect>,
  private val depositOperations: FakeDepositOperations
) : Repository<DepositToken, Deposit, DepositState, DepositEffect>, TestFake() {

  override fun saveWithOutbox(
    value: Deposit,
    outboxMessages: List<OutboxMessage<DepositToken, DepositEffect>>
  ): Result<Deposit> {
    val result = if (depositOperations.findByToken(value.id).getOrNull() == null) {
      depositOperations.insert(value)
    } else {
      depositOperations.update(value)
    }
    
    return result.onSuccess {
      outboxMessages.forEach { outbox.add(it) }
    }
  }

  fun findById(id: DepositToken): Deposit? = depositOperations.findByToken(id).getOrNull()

  fun all(): List<Deposit> = emptyList()
}
