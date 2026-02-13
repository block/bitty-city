package xyz.block.bittycity.innie.testing

import app.cash.kfsm.v2.AwaitableStateMachine
import app.cash.kfsm.v2.EffectProcessor
import app.cash.kfsm.v2.StateMachine

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Scopes
import com.google.inject.TypeLiteral
import jakarta.inject.Singleton
import xyz.block.bittycity.innie.fsm.DepositEffect
import xyz.block.bittycity.innie.fsm.DepositEffectHandler
import xyz.block.bittycity.innie.models.PendingReversal
import xyz.block.bittycity.innie.models.CollectingSanctionsInfo
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.Evicted
import xyz.block.bittycity.innie.models.Settled
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.Voided
import xyz.block.bittycity.innie.models.Reversed
import xyz.block.bittycity.innie.models.Sanctioned
import xyz.block.bittycity.innie.models.AwaitingDepositConfirmation
import xyz.block.bittycity.innie.models.AwaitingReversalConfirmation
import xyz.block.bittycity.innie.models.AwaitingReversalPendingConfirmation
import xyz.block.bittycity.innie.models.AwaitingSanctionsDecision
import kotlin.time.Duration.Companion.milliseconds

object TestStateMachineModule : AbstractModule() {

  override fun configure() {
    bind(object : TypeLiteral<InMemoryOutbox<DepositToken, DepositEffect>>() {})
      .toInstance(InMemoryOutbox())

    bind(InMemoryDepositRepository::class.java).`in`(Scopes.SINGLETON)
    bind(InMemoryPendingRequestStore::class.java).`in`(Scopes.SINGLETON)
  }

  @Provides
  @Singleton
  fun provideStateMachine(
    repository: InMemoryDepositRepository
  ): StateMachine<DepositToken, Deposit, DepositState, DepositEffect> =
    StateMachine(repository)

  @Provides
  @Singleton
  fun provideAwaitableStateMachine(
    stateMachine: StateMachine<DepositToken, Deposit, DepositState, DepositEffect>,
    pendingRequestStore: InMemoryPendingRequestStore
  ): AwaitableStateMachine<DepositToken, Deposit, DepositState, DepositEffect> =
    AwaitableStateMachine(
      stateMachine = stateMachine,
      pendingRequestStore = pendingRequestStore,
      isSettled = ::isSettled,
      pollInterval = 10.milliseconds
    )

  @Provides
  @Singleton
  fun provideEffectProcessor(
    outbox: InMemoryOutbox<DepositToken, DepositEffect>,
    handler: DepositEffectHandler,
    stateMachine: StateMachine<DepositToken, Deposit, DepositState, DepositEffect>,
    repository: InMemoryDepositRepository,
    awaitableStateMachine: AwaitableStateMachine<DepositToken, Deposit, DepositState, DepositEffect>
  ): EffectProcessor<DepositToken, Deposit, DepositState, DepositEffect> =
    EffectProcessor(
      outbox = outbox,
      handler = handler,
      stateMachine = stateMachine,
      valueLoader = { id -> Result.success(repository.findById(id)) },
      awaitable = awaitableStateMachine
    )
}

/**
 * Determines whether a deposit state is "settled" - meaning automatic effect processing
 * cannot continue and the workflow is either complete or waiting for external input.
 *
 * Settled states include:
 * - Terminal states (Settled, Voided, Reversed, Sanctioned)
 * - States awaiting external events (AwaitingDepositConfirmation, etc.)
 * - States awaiting user input (PendingReversal, CollectingSanctionsInfo)
 */
private fun isSettled(state: DepositState): Boolean = when (state) {
  // Terminal states
  Settled,
  Voided,
  Reversed,
  Sanctioned,
  // States waiting for external blockchain events
  AwaitingDepositConfirmation,
  Evicted,
  AwaitingReversalPendingConfirmation,
  AwaitingReversalConfirmation,
  // States waiting for user input or external decisions
  PendingReversal,
  CollectingSanctionsInfo,
  AwaitingSanctionsDecision -> true
  // All other states are intermediate - effects should continue processing
  else -> false
}
