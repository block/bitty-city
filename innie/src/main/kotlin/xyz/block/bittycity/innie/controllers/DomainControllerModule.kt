package xyz.block.bittycity.innie.controllers

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types.newParameterizedType
import jakarta.inject.Singleton
import xyz.block.bittycity.common.idempotency.IdempotentInputs
import xyz.block.bittycity.common.idempotency.IdempotentResumeInputs
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.innie.models.CheckingReversalSanctions
import xyz.block.bittycity.innie.models.CollectingReversalInfo
import xyz.block.bittycity.innie.models.CollectingReversalSanctionsInfo
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.models.WaitingForDepositConfirmedOnChainStatus
import xyz.block.bittycity.innie.store.ResponseOperations
import xyz.block.domainapi.kfsm.v2.util.Controller

object DomainControllerModule : AbstractModule() {
  @Provides
  @Singleton
  fun provideDomainController(
    onChainController: OnChainController,
    infoCollectionController: ReversalInfoCollectionController,
    sanctionsController: ReversalSanctionsController,
    sanctionsInfoCollectionController: ReversalSanctionsInfoCollectionController,
  ): DomainController<DepositToken, DepositState, Deposit, RequirementId> {
    val stateToController:
      Map<DepositState, Controller<DepositToken, DepositState, Deposit, RequirementId>> = mapOf(
        WaitingForDepositConfirmedOnChainStatus to onChainController,
        CollectingReversalInfo to infoCollectionController,
        CheckingReversalSanctions to sanctionsController,
        CollectingReversalSanctionsInfo to sanctionsInfoCollectionController,
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
