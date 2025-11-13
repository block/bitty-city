package xyz.block.bittycity.outie.testing

import app.cash.quiver.extensions.success
import xyz.block.bittycity.outie.client.FeeQuoteClient
import xyz.block.bittycity.common.models.FlatFee
import xyz.block.bittycity.outie.models.OnchainFeeQuote
import xyz.block.bittycity.outie.models.WithdrawalServiceFeeQuote
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.common.models.Bitcoins

class FakeFeeQuoteClient :
  TestFake(),
  FeeQuoteClient {
  var nextOnchainFeeQuote: Result<List<OnchainFeeQuote>> by resettable {
    listOf(
      OnchainFeeQuote(
        fee = Bitcoins(0L),
        blockTarget = 2,
      ),
      OnchainFeeQuote(
        fee = Bitcoins(0L),
        blockTarget = 12,
      ),
      OnchainFeeQuote(
        fee = Bitcoins(0L),
        blockTarget = 144,
      ),
    ).success()
  }

  var nextWithdrawalServiceFeeQuote: Result<List<WithdrawalServiceFeeQuote>> by resettable {
    listOf(
      WithdrawalServiceFeeQuote(
        speed = WithdrawalSpeed.PRIORITY,
        fee = FlatFee(Bitcoins(3000L))
      ),
      WithdrawalServiceFeeQuote(
        speed = WithdrawalSpeed.RUSH,
        fee = FlatFee(Bitcoins(2000L))
      ),
      WithdrawalServiceFeeQuote(
        speed = WithdrawalSpeed.STANDARD,
        fee = FlatFee(Bitcoins(0L))
      )
    ).success()
  }

  override fun quoteOnchainWithdrawalFees(
    customerId: String,
    destinationAddress: String
  ): Result<List<OnchainFeeQuote>> = Result.success(nextOnchainFeeQuote.getOrThrow())

  override fun quoteWithdrawalServiceFees(
    customerId: String,
    speeds: List<WithdrawalSpeed>
  ): Result<List<WithdrawalServiceFeeQuote>> = nextWithdrawalServiceFeeQuote
}
