package xyz.block.bittycity.outie.models

import org.joda.money.Money

/**
 * Contains information about a user's withdrawal limits and their current progress towards those limits.
 */
data class WithdrawalLimitInfo(
  val dailyLimit: Money,
  val dailyLimitProgress: Money,
  val weeklyLimit: Money,
  val weeklyLimitProgress: Money,
)
