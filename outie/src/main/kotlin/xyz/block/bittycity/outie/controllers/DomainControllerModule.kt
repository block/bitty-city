package xyz.block.bittycity.outie.controllers

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types.newParameterizedType
import jakarta.inject.Singleton
import xyz.block.bittycity.common.idempotency.IdempotentInputs
import xyz.block.bittycity.common.idempotency.IdempotentResumeInputs
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.outie.models.CollectingSanctionsInfo
import xyz.block.bittycity.outie.models.CollectingScamWarningDecision
import xyz.block.bittycity.outie.models.CollectingSelfAttestation
import xyz.block.bittycity.outie.models.Failed
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.SubmittingOnChain
import xyz.block.bittycity.outie.models.WaitingForConfirmedOnChainStatus
import xyz.block.bittycity.outie.models.WaitingForPendingConfirmationStatus
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.domainapi.util.Controller

object DomainControllerModule : AbstractModule() {
  @Provides
  @Singleton
  @Suppress("LongParameterList")
  fun provideDomainController(
    // All controllers marked with @ControllerDefinition
    infoCollectionController: InfoCollectionController,
    sanctionsInfoCollectionController: SanctionsInfoCollectionController,
    attestationInfoCollectionController: AttestationInfoCollectionController,
    scamWarningController: ScamWarningController,
    onChainController: OnChainController,
    failedController: FailedController,
  ): DomainController<WithdrawalToken, WithdrawalState, Withdrawal, RequirementId> {
    val stateToController:
      Map<WithdrawalState, Controller<WithdrawalToken, WithdrawalState, Withdrawal, RequirementId>> =
      mapOf(

        // InfoCollectionController handles CollectingInfo state
        CollectingInfo to infoCollectionController,

        // SanctionsInfoCollectionController handles CollectingSanctionsInfo state
        CollectingSanctionsInfo to sanctionsInfoCollectionController,

        // AttestationInfoCollectionController handles CollectingSelfAttestation state
        CollectingSelfAttestation to attestationInfoCollectionController,

        // ScamWarningController handles CollectingScamWarningDecision state
        CollectingScamWarningDecision to scamWarningController,

        // OnChainController handles all on-chain related states
        SubmittingOnChain to onChainController,
        WaitingForPendingConfirmationStatus to onChainController,
        WaitingForConfirmedOnChainStatus to onChainController,
        Failed.placeholder to failedController,
      ).mapValues { (_, controller) ->
        @Suppress("UNCHECKED_CAST")
        controller as Controller<WithdrawalToken, WithdrawalState, Withdrawal, RequirementId>
      }

    return DomainController(stateToController)
  }

  @Provides
  @Singleton
  fun provideIdempotencyHandler(
    moshi: Moshi,
    transactor: Transactor<xyz.block.bittycity.outie.store.ResponseOperations>
  ): IdempotencyHandler {
    return IdempotencyHandler(
      moshi = moshi,
      transactor = transactor,
      inputsAdapter = { m ->
        m.adapter(
          newParameterizedType(
            IdempotentInputs::class.java,
            WithdrawalToken::class.java,
            RequirementId::class.java
          )
        )
      },
      resumeInputsAdapter = { m ->
        m.adapter(
          newParameterizedType(
            IdempotentResumeInputs::class.java,
            WithdrawalToken::class.java,
            RequirementId::class.java
          )
        )
      }
    )
  }
}
