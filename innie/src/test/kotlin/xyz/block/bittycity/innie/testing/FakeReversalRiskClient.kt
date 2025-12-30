package xyz.block.bittycity.innie.testing

import app.cash.quiver.extensions.success
import xyz.block.bittycity.common.client.RiskClient
import xyz.block.bittycity.common.client.RiskEvaluation
import xyz.block.bittycity.common.client.RiskEvaluation.Checked
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.common.testing.TestFake
import xyz.block.bittycity.innie.models.DepositReversalToken

class FakeReversalRiskClient :
  TestFake(),
  RiskClient<DepositReversalToken> {

  var nextRiskResult: Result<RiskEvaluation> by resettable { Checked.success() }

  override fun evaluateRisk(
    customerId: CustomerId,
    token: DepositReversalToken
  ): Result<RiskEvaluation> = nextRiskResult
}
