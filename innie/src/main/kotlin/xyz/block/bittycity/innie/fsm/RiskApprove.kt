package xyz.block.bittycity.innie.fsm

import app.cash.kfsm.States
import xyz.block.bittycity.innie.models.CheckingDepositRisk
import xyz.block.bittycity.innie.models.Settled

class RiskApprove : DepositTransition(
  from = States(CheckingDepositRisk),
  to = Settled
)
