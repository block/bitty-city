package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.DeferrableEffect
import app.cash.kfsm.EffectPayload
import app.cash.kfsm.States
import app.cash.kfsm.annotations.ExperimentalLibraryApi
import arrow.core.raise.result
import com.squareup.moshi.Moshi
import jakarta.inject.Inject
import xyz.block.bittycity.outie.client.LedgerClient
import xyz.block.bittycity.outie.models.CollectingSanctionsInfo
import xyz.block.bittycity.outie.models.Sanctioned
import xyz.block.bittycity.outie.models.WaitingForSanctionsHeldDecision
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

@OptIn(ExperimentalLibraryApi::class)
class FreezeFunds @Inject constructor(
  private val ledgerClient: LedgerClient,
  moshi: Moshi
) :
  WithdrawalTransition(
    from = States(CollectingSanctionsInfo, WaitingForSanctionsHeldDecision),
    to = Sanctioned,
  ), DeferrableEffect<WithdrawalToken, Withdrawal, WithdrawalState> {
  val logger: KLogger = KotlinLogging.logger {}

  private val adapter = moshi.adapter(Withdrawal::class.java)

  override val effectType: String = "freeze_funds"

  override fun serialize(value: Withdrawal): Result<EffectPayload> = result {

    EffectPayload(
      effectType = effectType,
      data = adapter.toJson(value)
    )
  }

  override fun effect(value: Withdrawal): Result<Withdrawal> = result {
    logger.info {
      "Funds will be frozen. [token=${value.id}][ledgerTransaction=${value.ledgerTransactionId}]"
    }
    ledgerClient.freezeFunds(withdrawal = value).bind()
    value
  }
}
