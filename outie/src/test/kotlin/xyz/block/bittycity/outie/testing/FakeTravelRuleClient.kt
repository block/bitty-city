package xyz.block.bittycity.outie.testing

import app.cash.quiver.extensions.success
import xyz.block.bittycity.outie.client.TravelRuleClient
import xyz.block.bittycity.common.models.CustomerId
import org.bitcoinj.base.Address
import org.joda.money.Money

class FakeTravelRuleClient :
  TestFake(),
  TravelRuleClient {

  var nextAttestationResult: Result<Boolean> by resettable {
    false.success()
  }

  override fun requireSelfAttestation(
    targetWalletAddress: Address,
    fiatAmountEquivalent: Money,
    customerId: CustomerId
  ): Result<Boolean> = nextAttestationResult
}
