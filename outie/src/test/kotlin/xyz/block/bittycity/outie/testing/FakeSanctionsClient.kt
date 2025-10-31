package xyz.block.bittycity.outie.testing

import app.cash.quiver.extensions.success
import xyz.block.bittycity.outie.client.Evaluation
import xyz.block.bittycity.outie.client.SanctionsClient
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.models.Bitcoins
import org.bitcoinj.base.Address

class FakeSanctionsClient :
  TestFake(),
  SanctionsClient {

  var nextEvaluation by resettable { Evaluation.APPROVE.success() }

  override fun evaluateSanctions(
    customerId: String,
    withdrawalToken: WithdrawalToken,
    targetWalletAddress: Address,
    amount: Bitcoins?
  ): Result<Evaluation> = nextEvaluation
}
