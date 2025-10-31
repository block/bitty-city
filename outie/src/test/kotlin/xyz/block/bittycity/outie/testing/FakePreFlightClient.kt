package xyz.block.bittycity.outie.testing

import xyz.block.bittycity.outie.client.PreFlightClient
import xyz.block.bittycity.outie.models.Withdrawal

class FakePreFlightClient :
  TestFake(),
  PreFlightClient {
  override fun doPreFlight(value: Withdrawal): Result<Unit> = Result.success(Unit)
}
