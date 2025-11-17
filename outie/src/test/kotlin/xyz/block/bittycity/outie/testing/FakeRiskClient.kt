package xyz.block.bittycity.outie.testing

import xyz.block.bittycity.common.testing.TestFake

import app.cash.quiver.extensions.success
import xyz.block.bittycity.outie.client.RiskClient
import xyz.block.bittycity.outie.client.RiskEvaluation
import xyz.block.bittycity.outie.client.RiskEvaluation.Checked
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.outie.models.WithdrawalToken

class FakeRiskClient :
  TestFake(),
  RiskClient {

  var nextRiskResult: Result<RiskEvaluation> by resettable { Checked.success() }

  override fun evaluateRisk(
    customerId: CustomerId,
    withdrawalToken: WithdrawalToken
  ): Result<RiskEvaluation> = nextRiskResult
}
