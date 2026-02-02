package xyz.block.bittycity.outie.client

import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalLimitInfo
import xyz.block.domainapi.InfoOnly

/**
 * Evaluate withdrawal limits.
 */
interface LimitClient {
  /**
   * Evaluate withdrawal limits against product-specified limits.
   *
   * @param customerId The user's ID.
   * @param withdrawal the withdrawal to evaluate.
   * @return [LimitResponse] indicating if the withdrawal exceeds limits or not.
   */
  fun evaluateLimits(customerId: CustomerId, withdrawal: Withdrawal): Result<LimitResponse>

  fun getWithdrawalLimits(customerId: CustomerId): Result<WithdrawalLimitInfo>
}

/**
 * Represents the result of a check against limits.
 */
sealed class LimitResponse {
  /**
   * The withdrawal exceeds limits.
   *
   * @param violations The reasons the user is limited. Can be empty if the reasons are not known.
   */
  data class Limited(val violations: List<LimitViolation>) : LimitResponse()

  /**
   * The withdrawal does not exceed limits.
   */
  data object NotLimited : LimitResponse()
}

enum class LimitViolation {
  DAILY_USD_LIMIT,
  WEEKLY_USD_LIMIT,
}

data class LimitWouldBeExceeded(val violations: List<LimitViolation>) :
  Exception(
    "Withdrawal limits would be exceeded: $violations"
  ), InfoOnly
