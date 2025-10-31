package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.MachineBuilder.TransitionBuilder
import app.cash.kfsm.MachineBuilder.TransitionBuilder.ToBuilder
import app.cash.kfsm.StateMachine
import app.cash.kfsm.fsm
import com.google.inject.AbstractModule
import com.google.inject.Provides
import xyz.block.bittycity.outie.models.CheckingEligibility
import xyz.block.bittycity.outie.models.CheckingRisk
import xyz.block.bittycity.outie.models.CheckingSanctions
import xyz.block.bittycity.outie.models.CheckingTravelRule
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.outie.models.CollectingSanctionsInfo
import xyz.block.bittycity.outie.models.CollectingScamWarningDecision
import xyz.block.bittycity.outie.models.CollectingSelfAttestation
import xyz.block.bittycity.outie.models.ConfirmedComplete
import xyz.block.bittycity.outie.models.Failed
import xyz.block.bittycity.outie.models.HoldingSubmission
import xyz.block.bittycity.outie.models.Sanctioned
import xyz.block.bittycity.outie.models.SubmittingOnChain
import xyz.block.bittycity.outie.models.WaitingForConfirmedOnChainStatus
import xyz.block.bittycity.outie.models.WaitingForPendingConfirmationStatus
import xyz.block.bittycity.outie.models.WaitingForSanctionsHeldDecision
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Singleton

object StateMachineModule : AbstractModule() {

  @Provides
  @Singleton
  @Suppress("LongParameterList", "LongMethod")
  fun providesStateMachine(
    completeInformation: CompleteInformation,
    fail: Fail,
    freezeFunds: FreezeFunds,
    sanctionsHold: SanctionsHold,
    submittedOnChain: SubmittedOnChain,
    confirmedOnChain: ConfirmedOnChain,
    transitioner: WithdrawalTransitioner,
  ): StateMachine<WithdrawalToken, Withdrawal, WithdrawalState> {
    val logger: KLogger = KotlinLogging.logger("WithdrawalStateMachine")
    fun TransitionBuilder<WithdrawalToken, Withdrawal, WithdrawalState>.logOnly():
      ToBuilder<WithdrawalToken, Withdrawal, WithdrawalState>.(Withdrawal) -> Withdrawal =
      {
        it.also {
          logger.info { "Withdrawal (previously in $from) is now $to [id=${it.id}]" }
        }
      }

    return fsm<WithdrawalToken, Withdrawal, WithdrawalState>(transitioner) {
      CollectingInfo.becomes {
        CheckingSanctions via completeInformation
        Failed via fail
      }
      CheckingEligibility.becomes {
        Failed via fail
        HoldingSubmission via logOnly()
      }
      CheckingRisk.becomes {
        Failed via fail
        CheckingTravelRule via logOnly()
        CollectingScamWarningDecision via logOnly()
      }
      CollectingSanctionsInfo.becomes {
        CheckingEligibility via logOnly()
        Sanctioned via freezeFunds
        WaitingForSanctionsHeldDecision via logOnly()
      }
      CollectingSelfAttestation.becomes {
        Failed via fail
        CheckingEligibility via logOnly()
      }
      CollectingScamWarningDecision.becomes {
        CheckingTravelRule via logOnly()
        Failed via fail
      }
      CheckingSanctions.becomes {
        CheckingRisk via logOnly()
        CollectingSanctionsInfo via sanctionsHold
        Failed via fail
      }
      CheckingTravelRule.becomes {
        Failed via fail
        CheckingEligibility via logOnly()
        CollectingSelfAttestation via logOnly()
      }
      HoldingSubmission.becomes {
        Failed via fail
        SubmittingOnChain via logOnly()
      }
      SubmittingOnChain.becomes {
        Failed via fail
        WaitingForPendingConfirmationStatus via submittedOnChain
      }
      WaitingForConfirmedOnChainStatus.becomes {
        ConfirmedComplete via confirmedOnChain
        Failed via fail
      }
      WaitingForPendingConfirmationStatus.becomes {
        WaitingForConfirmedOnChainStatus via logOnly()
        ConfirmedComplete via confirmedOnChain
        Failed via fail
      }
      WaitingForSanctionsHeldDecision.becomes {
        CheckingEligibility via logOnly()
        Failed via fail
        Sanctioned via freezeFunds
      }
    }.getOrThrow()
  }

}
