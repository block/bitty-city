package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.States
import app.cash.kfsm.Transition
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken

/**
 * Base class representing all possible transitions for a withdrawal.
 */
abstract class WithdrawalTransition(
  from: States<WithdrawalToken, Withdrawal, WithdrawalState>,
  to: WithdrawalState
) : Transition<WithdrawalToken, Withdrawal, WithdrawalState>(from, to) {
  constructor(from: WithdrawalState, to: WithdrawalState) : this(States(from), to)
}
