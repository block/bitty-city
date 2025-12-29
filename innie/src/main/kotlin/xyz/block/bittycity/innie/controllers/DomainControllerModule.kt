package xyz.block.bittycity.innie.controllers

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types.newParameterizedType
import jakarta.inject.Singleton
import xyz.block.bittycity.common.idempotency.IdempotentInputs
import xyz.block.bittycity.common.idempotency.IdempotentResumeInputs
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.innie.models.CheckingDepositRisk
import xyz.block.bittycity.innie.models.CheckingEligibility
import xyz.block.bittycity.innie.models.CheckingReversalRisk
import xyz.block.bittycity.innie.models.CheckingSanctions
import xyz.block.bittycity.innie.models.CollectingInfo
import xyz.block.bittycity.innie.models.CollectingSanctionsInfo
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.models.WaitingForDepositConfirmedOnChainStatus
import xyz.block.bittycity.innie.models.WaitingForReversal
import xyz.block.bittycity.innie.store.ResponseOperations
import xyz.block.domainapi.util.Controller

object DomainControllerModule : AbstractModule() {
  @Provides
  @Singleton
  fun provideDomainController(
    onChainController: OnChainController,
    eligibilityController: EligibilityController,
    depositRiskController: DepositRiskController,
    depositReversalController: ReversalController,
    infoCollectionController: ReversalInfoCollectionController,
    sanctionsController: ReversalSanctionsController,
    sanctionsInfoCollectionController: ReversalSanctionsInfoCollectionController,
    depositReversalRiskController: ReversalRiskController
  ): DomainController<DepositToken, DepositState, Deposit, RequirementId> {
    val stateToController:
      Map<DepositState, Controller<DepositToken, DepositState, Deposit, RequirementId>> = mapOf(
        WaitingForDepositConfirmedOnChainStatus to onChainController,
        CheckingEligibility to eligibilityController,
        CheckingDepositRisk to depositRiskController,
        WaitingForReversal to depositReversalController,
        CollectingInfo to infoCollectionController,
        CheckingSanctions to sanctionsController,
        CollectingSanctionsInfo to sanctionsInfoCollectionController,
        CheckingReversalRisk to depositReversalRiskController
      ).mapValues { (_, controller) ->
        controller as Controller<DepositToken, DepositState, Deposit, RequirementId>
    }
    return DomainController(stateToController)
  }

  @Provides
  @Singleton
  fun provideIdempotencyHandler(
    moshi: Moshi,
    transactor: Transactor<ResponseOperations>
  ): IdempotencyHandler {
    return IdempotencyHandler(
      moshi = moshi,
      transactor = transactor,
      inputsAdapter = { m ->
        m.adapter(
          newParameterizedType(
            IdempotentInputs::class.java,
            DepositToken::class.java,
            RequirementId::class.java
          )
        )
      },
      resumeInputsAdapter = { m ->
        m.adapter(
          newParameterizedType(
            IdempotentResumeInputs::class.java,
            DepositToken::class.java,
            RequirementId::class.java
          )
        )
      }
    )
  }
}
