package xyz.block.bittycity.outie.testing

import app.cash.quiver.extensions.success
import xyz.block.bittycity.common.client.Eligibility
import xyz.block.bittycity.common.client.Eligibility.Eligible
import xyz.block.bittycity.common.testing.TestFake
import xyz.block.bittycity.outie.client.WithdrawalEligibilityClient

class FakeEligibilityClient :
  TestFake(),
  WithdrawalEligibilityClient {

  var nextEligibilityResult: Result<Eligibility> by resettable { Eligible(emptyList()).success() }

  override fun productEligibility(customerId: String): Result<Eligibility> =
    nextEligibilityResult
}
