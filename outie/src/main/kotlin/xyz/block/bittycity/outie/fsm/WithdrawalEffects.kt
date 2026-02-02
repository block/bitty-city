package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.Effect
import org.bitcoinj.base.Address
import org.joda.money.Money
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.common.models.LedgerTransactionId
import xyz.block.bittycity.outie.client.WithdrawalEvent.EventType
import xyz.block.bittycity.outie.models.BalanceId
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.models.WithdrawalToken

sealed class WithdrawalEffect : Effect {
  data class CreateTransaction(val withdrawal: Withdrawal) : WithdrawalEffect()
  data class ConfirmTransaction(
    val customerId: CustomerId,
    val balanceId: BalanceId,
    val ledgerTransactionId: LedgerTransactionId
  ) : WithdrawalEffect()
  data class VoidTransaction(
    val customerId: CustomerId,
    val balanceId: BalanceId,
    val ledgerTransactionId: LedgerTransactionId?
  ) : WithdrawalEffect()
  data class RefundFee(val withdrawal: Withdrawal) : WithdrawalEffect()
  data class FreezeFunds(val withdrawal: Withdrawal) : WithdrawalEffect()
  data class SubmitOnChain(
    val withdrawalToken: WithdrawalToken,
    val customerId: CustomerId,
    val targetWalletAddress: Address,
    val amount: Bitcoins,
    val fee: Bitcoins,
    val selectedSpeed: WithdrawalSpeed
  ) : WithdrawalEffect()
  data class CheckSanctions(
    val withdrawalToken: WithdrawalToken,
    val customerId: CustomerId,
    val targetWalletAddress: Address,
    val amount: Bitcoins
  ) : WithdrawalEffect()
  data class CheckRisk(val withdrawalToken: WithdrawalToken, val customerId: CustomerId) : WithdrawalEffect()
  data class CheckTravelRule(
    val customerId: CustomerId,
    val targetWalletAddress: Address,
    val fiatAmount: Money
  ) : WithdrawalEffect()
  data class CheckEligibility(val customerId: CustomerId) : WithdrawalEffect()
  data class CheckLimits(val withdrawal: Withdrawal) : WithdrawalEffect()
  data class PublishEvent(
    val oldWithdrawal: Withdrawal,
    val newWithdrawal: Withdrawal,
    val eventType: EventType
  ) : WithdrawalEffect()
}