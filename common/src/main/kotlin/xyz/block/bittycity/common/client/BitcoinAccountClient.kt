package xyz.block.bittycity.common.client

import xyz.block.bittycity.common.models.BitcoinAccount
import xyz.block.bittycity.common.models.CustomerId

interface BitcoinAccountClient {
  fun getBitcoinAccounts(customerId: CustomerId): Result<List<BitcoinAccount>>
}
