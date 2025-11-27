package xyz.block.bittycity.outie.fsm
 
import app.cash.kfsm.DeferrableEffect
import app.cash.kfsm.EffectPayload
import app.cash.kfsm.States
import app.cash.kfsm.annotations.ExperimentalLibraryApi
import arrow.core.raise.result
import com.squareup.moshi.Moshi
import xyz.block.bittycity.outie.client.LedgerClient
import xyz.block.bittycity.outie.client.OnChainClient
import xyz.block.bittycity.outie.client.WithdrawRequest.Companion.toWithdrawalRequest
import xyz.block.bittycity.outie.models.ConfirmedComplete
import xyz.block.bittycity.outie.models.SubmittingOnChain
import xyz.block.bittycity.outie.models.WaitingForConfirmedOnChainStatus
import xyz.block.bittycity.outie.models.WaitingForPendingConfirmationStatus
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.validation.ParameterIsRequired
import jakarta.inject.Inject
 
@OptIn(ExperimentalLibraryApi::class)
class SubmittedOnChain @Inject constructor(
  private val onChainClient: OnChainClient,
  moshi: Moshi
) :
  WithdrawalTransition(
    from = States(SubmittingOnChain),
    to = WaitingForPendingConfirmationStatus
  ), DeferrableEffect<WithdrawalToken, Withdrawal, WithdrawalState> {
 
  override val effectType = "submitted_on_chain"

  private val adapter = moshi.adapter(Withdrawal::class.java)
 
  override fun serialize(value: Withdrawal): Result<EffectPayload> = result {
    EffectPayload(
      effectType = effectType,
      data = adapter.toJson(value)
    )
  }
 
  override fun effect(value: Withdrawal): Result<Withdrawal> = result {
    val request = value.toWithdrawalRequest().bind()
    onChainClient.submitWithdrawal(request).bind()
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
