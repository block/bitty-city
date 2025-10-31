package xyz.block.bittycity.outie.client

import xyz.block.bittycity.outie.models.OnchainFeeQuote
import xyz.block.bittycity.outie.models.WithdrawalServiceFeeQuote
import xyz.block.bittycity.outie.models.WithdrawalSpeed

/**
 * Service for quoting withdrawal fees.
 */
interface FeeQuoteClient {

  /**
   * Retrieves withdrawal fee quotes for the given destination address.
   *
   * @param customerId The customer ID of the user requesting the quote.
   * @param destinationAddress The destination address of the withdrawal.
   * @return a [Result] containing a list of [OnchainFeeQuote] objects.
   */
  fun quoteOnchainWithdrawalFees(
    customerId: String,
    destinationAddress: String
  ): Result<List<OnchainFeeQuote>>

  /**
   * Retrieves additional fees for withdrawal services for multiple block targets.
   *
   * @param customerId The customer ID of the user requesting the quote.
   * @param blockTargets The list of block targets to quote fees for.
   * @return a [Result] containing a list of [WithdrawalServiceFeeQuote] objects.
   */
  fun quoteWithdrawalServiceFees(
    customerId: String,
    speeds: List<WithdrawalSpeed>,
  ): Result<List<WithdrawalServiceFeeQuote>>
}
