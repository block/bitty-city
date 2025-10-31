package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.States
import arrow.core.raise.result
import xyz.block.bittycity.outie.models.CollectingSanctionsInfo
import xyz.block.bittycity.outie.models.Sanctioned
import xyz.block.bittycity.outie.models.WaitingForSanctionsHeldDecision
import xyz.block.bittycity.outie.models.Withdrawal
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

class FreezeFunds :
  WithdrawalTransition(
    from = States(CollectingSanctionsInfo, WaitingForSanctionsHeldDecision),
    to = Sanctioned,
  ) {
  val logger: KLogger = KotlinLogging.logger {}

  override fun effect(value: Withdrawal): Result<Withdrawal> = result {
    // Only log here - the effect is executed in the event processor
    logger.info {
      "Funds will be frozen. [token=${value.id}][ledgerTransaction=${value.ledgerTransactionId}]"
    }
    value
  }
}
