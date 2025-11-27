package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.DeferrableEffect
import app.cash.kfsm.EffectPayload
import app.cash.kfsm.States
import app.cash.kfsm.annotations.ExperimentalLibraryApi
import arrow.core.raise.result
import com.squareup.moshi.Moshi
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
import xyz.block.bittycity.outie.models.WithdrawalToken
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject
import xyz.block.bittycity.outie.client.LedgerClient
import xyz.block.bittycity.outie.models.WithdrawalState

/**
 * Fails the withdrawal. Any prior funds reservation is voided.
 */

@OptIn(ExperimentalLibraryApi::class)
class Fail @Inject constructor(
  private val ledgerClient: LedgerClient,
  moshi: Moshi
) :
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
  ), DeferrableEffect<WithdrawalToken, Withdrawal, WithdrawalState> {
  val logger: KLogger = KotlinLogging.logger {}

  override val effectType: String = "fail"

  private val adapter = moshi.adapter(Withdrawal::class.java)

  override fun serialize(value: Withdrawal): Result<EffectPayload> = result {
    EffectPayload(
      effectType = effectType,
      data = adapter.toJson(value)
    )
  }

  override fun effect(value: Withdrawal): Result<Withdrawal> = result {
    logger.info {
      "Failing withdrawal. " +
              "[token=${value.id}]" +
              "[reason=${value.failureReason}]" +
              "[ledgerTransaction=${value.ledgerTransactionId}]"
    }
    value.ledgerTransactionId?.let {
      ledgerClient.voidTransaction(
        customerId = value.customerId,
        balanceId = value.sourceBalanceToken,
        ledgerTransactionId = it
      ).bind()
    }
    value
  }
}
