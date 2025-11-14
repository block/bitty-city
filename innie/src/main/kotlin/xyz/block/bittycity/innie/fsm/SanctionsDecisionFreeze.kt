package xyz.block.bittycity.innie.fsm

import app.cash.kfsm.States
import xyz.block.bittycity.innie.models.CollectingSanctionsInfo
import xyz.block.bittycity.innie.models.Sanctioned
import xyz.block.bittycity.innie.models.WaitingForSanctionsHeldDecision

class SanctionsDecisionFreeze : DepositTransition(
  from = States(CollectingSanctionsInfo, WaitingForSanctionsHeldDecision),
  to = Sanctioned
)
