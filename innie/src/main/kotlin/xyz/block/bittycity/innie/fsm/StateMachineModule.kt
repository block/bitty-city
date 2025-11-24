package xyz.block.bittycity.innie.fsm

import app.cash.kfsm.MachineBuilder.TransitionBuilder
import app.cash.kfsm.MachineBuilder.TransitionBuilder.ToBuilder
import app.cash.kfsm.StateMachine
import app.cash.kfsm.fsm
import com.google.inject.AbstractModule
import com.google.inject.Provides
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Singleton
import xyz.block.bittycity.innie.models.CheckingDepositRisk
import xyz.block.bittycity.innie.models.CheckingEligibility
import xyz.block.bittycity.innie.models.CheckingReversalRisk
import xyz.block.bittycity.innie.models.CheckingSanctions
import xyz.block.bittycity.innie.models.CollectingInfo
import xyz.block.bittycity.innie.models.CollectingSanctionsInfo
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.ExpiredPending
import xyz.block.bittycity.innie.models.ReversalConfirmedComplete
import xyz.block.bittycity.innie.models.Sanctioned
import xyz.block.bittycity.innie.models.Settled
import xyz.block.bittycity.innie.models.Voided
import xyz.block.bittycity.innie.models.WaitingForDepositConfirmedOnChainStatus
import xyz.block.bittycity.innie.models.WaitingForReversal
import xyz.block.bittycity.innie.models.WaitingForReversalConfirmedOnChainStatus
import xyz.block.bittycity.innie.models.WaitingForReversalPendingConfirmationStatus
import xyz.block.bittycity.innie.models.WaitingForSanctionsHeldDecision

object StateMachineModule  : AbstractModule() {

  @Provides
  @Singleton
  @Suppress("LongParameterList", "LongMethod")
  fun providesStateMachine(
    riskApprove: RiskApprove,
    sanctionsDecisionFreeze: SanctionsDecisionFreeze,
    transitioner: DepositTransitioner,
  ): StateMachine<DepositToken, Deposit, DepositState> {
    val logger: KLogger = KotlinLogging.logger("DepositStateMachine")
    fun TransitionBuilder<DepositToken, Deposit, DepositState>.logOnly():
            ToBuilder<DepositToken, Deposit, DepositState>.(Deposit) -> Deposit =
      {
        it.also {
          logger.info { "Deposit (previously in $from) is now $to [id=${it.id}]" }
        }
      }

    return fsm<DepositToken, Deposit, DepositState>(transitioner) {
      WaitingForDepositConfirmedOnChainStatus becomes {
        ExpiredPending via logOnly()
        CheckingEligibility via logOnly()
        Voided via logOnly()
      }
      ExpiredPending becomes {
        CheckingEligibility via logOnly()
        Voided via logOnly()
      }
      CheckingEligibility becomes {
        CheckingDepositRisk via logOnly()
        WaitingForReversal via logOnly()
      }
      CheckingDepositRisk becomes {
        Settled via riskApprove
        WaitingForReversal via logOnly()
      }
      WaitingForReversal becomes {
        CollectingInfo via logOnly()
      }
      CollectingInfo becomes {
        WaitingForReversal via logOnly()
        CheckingSanctions via logOnly()
      }
      CheckingSanctions becomes {
        CheckingReversalRisk via logOnly()
        CollectingSanctionsInfo via logOnly()
        WaitingForReversal via logOnly()
      }
      CheckingReversalRisk becomes {
        WaitingForReversalPendingConfirmationStatus via logOnly()
        WaitingForReversal via logOnly()
      }
      CollectingSanctionsInfo becomes {
        WaitingForSanctionsHeldDecision via logOnly()
        Sanctioned via sanctionsDecisionFreeze
        WaitingForReversal via logOnly()
      }
      WaitingForSanctionsHeldDecision becomes {
        WaitingForReversalPendingConfirmationStatus via logOnly()
        Sanctioned via sanctionsDecisionFreeze
        WaitingForReversal via logOnly()
      }
      WaitingForReversalPendingConfirmationStatus  becomes {
        WaitingForReversalConfirmedOnChainStatus via logOnly()
        WaitingForReversal via logOnly()
      }
      WaitingForReversalConfirmedOnChainStatus becomes {
        ReversalConfirmedComplete via logOnly()
        WaitingForReversal via logOnly()
      }
    }.getOrThrow()
  }

}
