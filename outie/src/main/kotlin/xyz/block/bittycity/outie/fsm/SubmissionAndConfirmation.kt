package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.States
import arrow.core.raise.result
import xyz.block.bittycity.outie.client.LedgerClient
import xyz.block.bittycity.outie.client.WithdrawRequest.Companion.toWithdrawalRequest
import xyz.block.bittycity.outie.models.ConfirmedComplete
import xyz.block.bittycity.outie.models.SubmittingOnChain
import xyz.block.bittycity.outie.models.WaitingForConfirmedOnChainStatus
import xyz.block.bittycity.outie.models.WaitingForPendingConfirmationStatus
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.validation.ParameterIsRequired
import jakarta.inject.Inject

class SubmittedOnChain :
  WithdrawalTransition(
    from = States(SubmittingOnChain),
    to = WaitingForPendingConfirmationStatus
  ) {

  override fun effect(value: Withdrawal): Result<Withdrawal> = result {
    // Only validate here - the effect is executed in the event processor
    value.toWithdrawalRequest().bind()
    value
  }
}

class ConfirmedOnChain @Inject constructor(private val ledgerClient: LedgerClient) :
  WithdrawalTransition(
    from = States(WaitingForPendingConfirmationStatus, WaitingForConfirmedOnChainStatus),
    to = ConfirmedComplete,
  ) {

  override fun effect(value: Withdrawal): Result<Withdrawal> = result {
    val ledgerTransactionId = value.ledgerTransactionId
      ?: raise(ParameterIsRequired(value.customerId, "ledgerTransactionId"))
    ledgerClient.confirmTransaction(
      customerId = value.customerId,
      balanceId = value.sourceBalanceToken,
      ledgerTransactionId = ledgerTransactionId
    ).bind()
    value
  }
}
