package xyz.block.bittycity.outie.fsm

import arrow.core.raise.result
import xyz.block.bittycity.outie.client.LedgerClient
import xyz.block.bittycity.outie.models.CheckingSanctions
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.outie.models.Withdrawal
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject

class CompleteInformation @Inject constructor(private val ledgerClient: LedgerClient) :
  WithdrawalTransition(
    from = CollectingInfo,
    to = CheckingSanctions
  ) {
  val logger: KLogger = KotlinLogging.logger {}

  override fun effect(value: Withdrawal): Result<Withdrawal> = result {
    logger.info { "Creating ledger transaction for Withdrawal ${value.id}" }
    val transactionId = ledgerClient.createTransaction(value).bind()
    value.copy(ledgerTransactionId = transactionId)
  }
}
