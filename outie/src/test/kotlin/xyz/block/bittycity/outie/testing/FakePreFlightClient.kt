package xyz.block.bittycity.outie.testing

import xyz.block.bittycity.common.client.PreFlightClient
import xyz.block.bittycity.outie.models.Withdrawal

class FakePreFlightClient :
  TestFake(),
  PreFlightClient<Withdrawal> {
  override fun doPreFlight(value: Withdrawal): Result<Unit> = Result.success(Unit)
}
