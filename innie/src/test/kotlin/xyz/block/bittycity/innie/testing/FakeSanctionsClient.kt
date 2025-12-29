package xyz.block.bittycity.innie.testing

import app.cash.quiver.extensions.success
import org.bitcoinj.base.Address
import xyz.block.bittycity.common.client.Evaluation
import xyz.block.bittycity.common.client.SanctionsClient
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.testing.TestFake
import xyz.block.bittycity.innie.models.DepositReversalToken

class FakeSanctionsClient :
  TestFake(),
  SanctionsClient<DepositReversalToken> {

  var nextEvaluation by resettable { Evaluation.APPROVE.success() }

  override fun evaluateSanctions(
    customerId: String,
    transactionToken: DepositReversalToken,
    targetWalletAddress: Address,
    amount: Bitcoins?
  ): Result<Evaluation> = nextEvaluation
}
