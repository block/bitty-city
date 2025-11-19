package xyz.block.bittycity.innie.controllers

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types.newParameterizedType
import jakarta.inject.Singleton
import xyz.block.bittycity.common.idempotency.IdempotentInputs
import xyz.block.bittycity.common.idempotency.IdempotentResumeInputs
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.store.ResponseOperations

object DomainControllerModule : AbstractModule() {

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
