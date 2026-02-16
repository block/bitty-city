package xyz.block.bittycity.innie.fsm

import app.cash.kfsm.v2.Effect
import org.bitcoinj.base.Address
import org.joda.money.Money
import xyz.block.bittycity.common.models.BalanceId
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.common.models.LedgerTransactionId
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositFailureReason
import xyz.block.bittycity.innie.models.DepositReversalFailureReason
import xyz.block.bittycity.innie.models.DepositReversalToken
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import java.time.Instant

sealed class DepositEffect : Effect {
  data class RequestDepositTransactionCreation(
    val depositId: DepositToken,
    val customerId: CustomerId,
    val balanceId: BalanceId,
    val createdAt: Instant,
    val amount: Bitcoins,
    val fiatEquivalent: Money
  ) : DepositEffect()
  data class ConfirmDepositTransaction(
    val customerId: CustomerId,
    val balanceId: BalanceId,
    val ledgerTransactionId: LedgerTransactionId
  ) : DepositEffect()
  data class VoidDepositTransaction(
    val customerId: CustomerId,
    val balanceId: BalanceId,
    val ledgerTransactionId: LedgerTransactionId?
  ) : DepositEffect()
  data class FreezeReversal(
    val depositReversalId: DepositReversalToken,
    val customerId: CustomerId,
    val balanceId: BalanceId,
    val createdAt: Instant,
    val amount: Bitcoins,
    val fiatEquivalent: Money,
    val targetWalletAddress: Address
  ) : DepositEffect()
  data class ConfirmReversalTransaction(
    val customerId: CustomerId,
    val balanceId: BalanceId,
    val ledgerTransactionId: LedgerTransactionId
  ) : DepositEffect()
  data class RequestDepositEligibilityCheck(val customerId: CustomerId) : DepositEffect()
  data class RequestDepositRiskCheck(val customerId: CustomerId, val depositId: DepositToken) : DepositEffect()
  data class RequestReversalSanctionsCheck(
    val customerId: CustomerId,
    val reversalId: DepositReversalToken,
    val reversalTargetWalletAddress: Address,
    val amount: Bitcoins
  ) : DepositEffect()
  data class RequestReversalRiskCheck(val customerId: CustomerId, val depositReversalId: DepositReversalToken) : DepositEffect()
  data class PublishStateTransitionMetric(
    val from: DepositState,
    val to: DepositState,
    val failureReason: DepositFailureReason?
  ) : DepositEffect()
  data class PublishFailureReasonMetric(val reason: DepositFailureReason) : DepositEffect()
  data class PublishReversalFailureReasonMetric(val reason: DepositReversalFailureReason) : DepositEffect()
  data class PublishDepositSuccessAmountMetric(val deposit: Deposit) : DepositEffect()
}
