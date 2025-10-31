package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.States
import arrow.core.raise.result
import xyz.block.bittycity.outie.models.CheckingEligibility
import xyz.block.bittycity.outie.models.CheckingRisk
import xyz.block.bittycity.outie.models.CheckingSanctions
import xyz.block.bittycity.outie.models.CheckingTravelRule
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.outie.models.CollectingScamWarningDecision
import xyz.block.bittycity.outie.models.CollectingSelfAttestation
import xyz.block.bittycity.outie.models.Failed
import xyz.block.bittycity.outie.models.HoldingSubmission
import xyz.block.bittycity.outie.models.SubmittingOnChain
import xyz.block.bittycity.outie.models.WaitingForConfirmedOnChainStatus
import xyz.block.bittycity.outie.models.WaitingForPendingConfirmationStatus
import xyz.block.bittycity.outie.models.WaitingForSanctionsHeldDecision
import xyz.block.bittycity.outie.models.Withdrawal
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Fails the withdrawal. Any prior funds reservation is voided.
 */

class Fail :
  WithdrawalTransition(
    from = States(
      CollectingInfo,
      CollectingSelfAttestation,
      CheckingRisk,
      CollectingScamWarningDecision,
      HoldingSubmission,
      CheckingSanctions,
      SubmittingOnChain,
      WaitingForSanctionsHeldDecision,
      CheckingTravelRule,
      CheckingEligibility,
      WaitingForPendingConfirmationStatus,
      WaitingForConfirmedOnChainStatus
    ),
    to = Failed,
  ) {
  val logger: KLogger = KotlinLogging.logger {}

  override fun effect(value: Withdrawal): Result<Withdrawal> = result {
    // Only log here - the effect is executed in the event processor
    logger.info { "Withdrawal will be failed. [token=${value.id}]" }
    value.ledgerTransactionId?.let {
      logger.info { "Ledger transaction will be voided. [ledgerTransaction=$it]" }
    }
    value
  }
}
