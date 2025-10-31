package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.States
import arrow.core.raise.result
import xyz.block.bittycity.outie.client.LedgerClient
import xyz.block.bittycity.outie.models.CheckingSanctions
import xyz.block.bittycity.outie.models.CollectingSanctionsInfo
import xyz.block.bittycity.outie.models.Withdrawal
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject

class SanctionsHold @Inject constructor(private val ledgerClient: LedgerClient) :
  WithdrawalTransition(
    from = States(CheckingSanctions),
    to = CollectingSanctionsInfo,
  ) {
  val logger: KLogger = KotlinLogging.logger {}

  override fun effect(value: Withdrawal): Result<Withdrawal> = result {
    logger.info {
      "Refunding fee for withdrawal because it was flagged for manual sanctions review. " +
        "[token=${value.id}]" +
        "[ledgerTransaction=${value.ledgerTransactionId}]"
    }
    val transactionId = ledgerClient.refundFee(value).bind()
    value.copy(
      feeRefunded = true,
      ledgerTransactionId = transactionId
    )
  }
}
