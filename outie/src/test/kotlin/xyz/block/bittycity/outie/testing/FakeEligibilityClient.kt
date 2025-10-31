package xyz.block.bittycity.outie.testing

import app.cash.quiver.extensions.success
import xyz.block.bittycity.outie.client.Eligibility
import xyz.block.bittycity.outie.client.Eligibility.Eligible
import xyz.block.bittycity.outie.client.EligibilityClient

class FakeEligibilityClient :
  TestFake(),
  EligibilityClient {

  var nextEligibilityResult: Result<Eligibility> by resettable { Eligible(emptyList()).success() }

  override fun productEligibility(customerId: String): Result<Eligibility> =
    nextEligibilityResult
}
