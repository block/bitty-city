package xyz.block.bittycity.outie.testing

import xyz.block.bittycity.common.testing.TestFake

import xyz.block.bittycity.outie.client.EventClient
import xyz.block.bittycity.outie.client.WithdrawalEvent
import jakarta.inject.Inject

class FakeEventClient @Inject constructor() :
  TestFake(),
  EventClient {
  val published: MutableList<WithdrawalEvent> by resettable { mutableListOf() }

  override fun publish(event: WithdrawalEvent): Result<Unit> {
    published.add(event)
    return Result.success(Unit)
  }
}
