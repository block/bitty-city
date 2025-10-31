package xyz.block.bittycity.outie.testing

import app.cash.quiver.extensions.success
import xyz.block.bittycity.outie.client.BitcoinAccountClient
import xyz.block.bittycity.outie.models.BitcoinAccount
import xyz.block.bittycity.outie.models.CustomerId

class FakeBitcoinAccountClient :
  TestFake(),
  BitcoinAccountClient {

  var nextBitcoinAccount: BitcoinAccount? by resettable { null }

  override fun getBitcoinAccounts(customerId: CustomerId): Result<List<BitcoinAccount>> =
    listOfNotNull(nextBitcoinAccount).success()
}
