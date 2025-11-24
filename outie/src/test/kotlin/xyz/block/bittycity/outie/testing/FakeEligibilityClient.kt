package xyz.block.bittycity.outie.testing

import xyz.block.bittycity.common.testing.TestFake

import app.cash.quiver.extensions.success
import xyz.block.bittycity.common.client.Eligibility
import xyz.block.bittycity.common.client.Eligibility.Eligible
import xyz.block.bittycity.common.client.EligibilityClient

class FakeEligibilityClient :
  TestFake(),
  EligibilityClient {

  var nextEligibilityResult: Result<Eligibility> by resettable { Eligible(emptyList()).success() }

  override fun productEligibility(customerId: String): Result<Eligibility> =
    nextEligibilityResult
}
