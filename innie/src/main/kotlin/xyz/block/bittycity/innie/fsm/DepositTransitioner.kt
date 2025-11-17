package xyz.block.bittycity.innie.fsm

import app.cash.kfsm.Transition
import app.cash.kfsm.Transitioner
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken

class DepositTransitioner () : Transitioner<
        DepositToken,
        Transition<DepositToken, Deposit, DepositState>,
        Deposit,
        DepositState
        >() {
}
