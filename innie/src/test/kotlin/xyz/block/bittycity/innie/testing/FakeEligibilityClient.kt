package xyz.block.bittycity.innie.testing

import app.cash.quiver.extensions.success
import xyz.block.bittycity.common.client.Eligibility
import xyz.block.bittycity.common.client.Eligibility.Eligible
import xyz.block.bittycity.common.testing.TestFake
import xyz.block.bittycity.innie.client.DepositEligibilityClient

class FakeEligibilityClient :
  TestFake(),
  DepositEligibilityClient {

  var nextEligibilityResult: Result<Eligibility> by resettable { Eligible(emptyList()).success() }

  override fun productEligibility(customerId: String): Result<Eligibility> =
    nextEligibilityResult
}
