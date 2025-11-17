package xyz.block.bittycity.outie.testing

import xyz.block.bittycity.common.testing.TestFake

import app.cash.quiver.extensions.failure
import app.cash.quiver.extensions.success
import xyz.block.bittycity.outie.client.LimitClient
import xyz.block.bittycity.outie.client.LimitResponse
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalLimitInfo
import jakarta.inject.Inject
import org.joda.money.CurrencyUnit
import org.joda.money.Money

class FakeLimitClient @Inject constructor() :
  TestFake(),
  LimitClient {
  var nextLimitResponse: Result<LimitResponse> by resettable { LimitResponse.NotLimited.success() }

  var nextLimitInfoResponse: Result<WithdrawalLimitInfo> by resettable {
    WithdrawalLimitInfo(
      dailyLimit = Money.ofMinor(CurrencyUnit.USD, 15_000_00),
      dailyLimitProgress = Money.ofMinor(CurrencyUnit.USD, 0),
      weeklyLimit = Money.ofMinor(CurrencyUnit.USD, 50_000_00),
      weeklyLimitProgress = Money.ofMinor(CurrencyUnit.USD, 0),
    ).success()
  }

  override fun evaluateLimits(
    customerId: CustomerId,
    withdrawal: Withdrawal
  ): Result<LimitResponse> = nextLimitResponse

  override fun getWithdrawalLimits(customerId: CustomerId): Result<WithdrawalLimitInfo> =
    nextLimitInfoResponse

  fun failNextEvaluation(): Exception =
    Exception("\uD83D\uDC1F Something fishy (expected exception during testing)")
      .also { nextLimitResponse = it.failure() }
}
