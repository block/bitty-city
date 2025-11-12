package xyz.block.bittycity.outie.client

import xyz.block.bittycity.outie.models.BitcoinAccount
import xyz.block.bittycity.common.models.CustomerId

interface BitcoinAccountClient {
  fun getBitcoinAccounts(customerId: CustomerId): Result<List<BitcoinAccount>>
}
