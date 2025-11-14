package xyz.block.bittycity.innie.fsm

import app.cash.kfsm.States
import app.cash.kfsm.Transition
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken

abstract class DepositTransition(
  from: States<DepositToken, Deposit, DepositState>,
  to: DepositState
) : Transition<DepositToken, Deposit, DepositState>(from, to) {
  constructor(from: DepositState, to: DepositState) : this(States(from), to)
}
