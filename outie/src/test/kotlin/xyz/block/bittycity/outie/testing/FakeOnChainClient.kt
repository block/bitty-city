package xyz.block.bittycity.outie.testing

import xyz.block.bittycity.common.testing.TestFake

import arrow.core.raise.result
import xyz.block.bittycity.outie.client.OnChainClient
import xyz.block.bittycity.outie.client.OnChainState
import xyz.block.bittycity.outie.client.WithdrawRequest
import xyz.block.bittycity.outie.client.WithdrawResponse
import xyz.block.bittycity.outie.models.WithdrawalToken
import java.util.concurrent.ConcurrentHashMap

class FakeOnChainClient :
  TestFake(),
  OnChainClient {

  var failNextCall: Boolean by resettable { false }

  private val submittedWithdrawals: MutableMap<WithdrawalToken, WithdrawRequest> by resettable {
    ConcurrentHashMap<WithdrawalToken, WithdrawRequest>()
  }

  override fun submitWithdrawal(request: WithdrawRequest): Result<WithdrawResponse> = result {
    if (failNextCall) {
      raise(FakeOnChainClientException)
    } else {
      submittedWithdrawals[request.withdrawalToken] = request
      WithdrawResponse(
        withdrawalToken = request.withdrawalToken,
        state = OnChainState.Submitted
      )
    }
  }

  fun getSubmittedWithdrawal(id: WithdrawalToken): WithdrawRequest? = submittedWithdrawals[id]
}

object FakeOnChainClientException: Throwable()
