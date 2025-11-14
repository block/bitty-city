package xyz.block.bittycity.innie.fsm

import app.cash.kfsm.Transition
import app.cash.kfsm.Transitioner
import jakarta.inject.Inject
import xyz.block.bittycity.common.client.PreFlightClient
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.innie.client.MetricsClient
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.store.DepositOperations

class DepositTransitioner @Inject constructor(
  private val depositTransactor: Transactor<DepositOperations>,
  private val preflightClient: PreFlightClient<Deposit>,
  private val metricsClient: MetricsClient,
)
  : Transitioner<
        DepositToken,
        Transition<DepositToken, Deposit, DepositState>,
        Deposit,
        DepositState
        >() {
}
