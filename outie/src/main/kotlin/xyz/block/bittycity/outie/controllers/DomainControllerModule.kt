package xyz.block.bittycity.outie.controllers

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types.newParameterizedType
import xyz.block.bittycity.common.idempotency.IdempotentInputs
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.outie.models.CheckingEligibility
import xyz.block.bittycity.outie.models.CheckingRisk
import xyz.block.bittycity.outie.models.CheckingSanctions
import xyz.block.bittycity.outie.models.CheckingTravelRule
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.outie.models.CollectingSanctionsInfo
import xyz.block.bittycity.outie.models.CollectingScamWarningDecision
import xyz.block.bittycity.outie.models.CollectingSelfAttestation
import xyz.block.bittycity.outie.models.HoldingSubmission
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.outie.models.SubmittingOnChain
import xyz.block.bittycity.outie.models.WaitingForConfirmedOnChainStatus
import xyz.block.bittycity.outie.models.WaitingForPendingConfirmationStatus
import xyz.block.bittycity.outie.models.WaitingForSanctionsHeldDecision
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import jakarta.inject.Singleton
import xyz.block.domainapi.util.Controller

object DomainControllerModule : AbstractModule() {
  @Provides
  @Singleton
  @Suppress("LongParameterList")
  fun provideDomainController(
    // All controllers marked with @ControllerDefinition
    eligibilityController: EligibilityController,
    infoCollectionController: InfoCollectionController,
    sanctionsController: SanctionsController,
    sanctionsInfoCollectionController: SanctionsInfoCollectionController,
    attestationInfoCollectionController: AttestationInfoCollectionController,
    riskController: RiskController,
    scamWarningController: ScamWarningController,
    travelRuleController: TravelRuleController,
    onChainController: OnChainController,
  ): DomainController<WithdrawalToken, WithdrawalState, Withdrawal, RequirementId> {
    val stateToController:
      Map<WithdrawalState, Controller<WithdrawalToken, WithdrawalState, Withdrawal, RequirementId>> =
      mapOf(
        // EligibilityController handles CheckingEligibility state
        CheckingEligibility to eligibilityController,

        // InfoCollectionController handles CollectingInfo state
        CollectingInfo to infoCollectionController,

        // SanctionsController handles CheckingSanctions and WaitingForSanctionsHeldDecision states
        CheckingSanctions to sanctionsController,
        WaitingForSanctionsHeldDecision to sanctionsController,

        // SanctionsInfoCollectionController handles CollectingSanctionsInfo state
        CollectingSanctionsInfo to sanctionsInfoCollectionController,

        // AttestationInfoCollectionController handles CollectingSelfAttestation state
        CollectingSelfAttestation to attestationInfoCollectionController,

        // RiskController handles CheckingRisk state
        CheckingRisk to riskController,

        // ScamWarningController handles CollectingScamWarningDecision state
        CollectingScamWarningDecision to scamWarningController,

        // TravelRuleController handles CheckingTravelRule state
        CheckingTravelRule to travelRuleController,

        // OnChainController handles all on-chain related states
        HoldingSubmission to onChainController,
        SubmittingOnChain to onChainController,
        WaitingForPendingConfirmationStatus to onChainController,
        WaitingForConfirmedOnChainStatus to onChainController,
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
      }
    )
  }
}
