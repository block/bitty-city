package xyz.block.bittycity.outie.models

import org.joda.money.Money
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.ServiceFee
import kotlin.time.Duration

data class WithdrawalSpeedOption(
  /** The id of this speed option. */
  val id: Long? = null,
  /** The speed and it's associated block target. */
  val speed: WithdrawalSpeed,
  /**
   * The total fee associated with the withdrawal, payable by the customer. Includes the raw fee
   * (amount required to submit on-chain and achieve the SLA for the chosen speed) plus any
   * additional fees on top. These can be calculated using a flat fee or a margin.
   */
  val totalFee: Bitcoins,
  /** The fiat equivalent of the total fee, at the time of creation. */
  val totalFeeFiatEquivalent: Money,
  /** the cost to us of submitting the withdrawal on-chain within the chosen SLAs. */
  val serviceFee: ServiceFee,
  val approximateWaitTime: Duration,
  /**
   * The minimum and maximum amounts that can be withdrawn using this speed. The client can use
   * these values to ensure that the user is only able to enter an amount that will result in at
   * least one selectable withdrawal speed.
   */
  val minimumAmount: Bitcoins? = null,
  val maximumAmount: Bitcoins? = null,
  val selectable: Boolean? = null,
  /**
   * If the original amount cannot be withdrawn because there wouldn't be enough funds to cover the
   * fee, then this is the maximum amount that could be withdrawn.
   */
  val adjustedAmount: Bitcoins? = null,
  val adjustedAmountFiatEquivalent: Money? = null,
) {
  companion object {
    const val BLOCK_TARGET_2 = 2
    const val BLOCK_TARGET_12 = 12
    const val BLOCK_TARGET_144 = 144
  }
}

enum class WithdrawalSpeed(val blockTarget: Int) {
  PRIORITY(WithdrawalSpeedOption.BLOCK_TARGET_2),
  RUSH(WithdrawalSpeedOption.BLOCK_TARGET_12),
  STANDARD(WithdrawalSpeedOption.BLOCK_TARGET_144),
}
