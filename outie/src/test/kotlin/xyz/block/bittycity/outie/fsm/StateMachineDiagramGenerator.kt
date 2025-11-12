package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.StateMachine
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import kotlin.system.exitProcess
import xyz.block.bittycity.common.fsm.StateMachineDiagramGenerator
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.TestPersistenceModule
import xyz.block.bittycity.outie.testing.TestModule
import java.io.File

/**
 * Generates a Mermaid state machine diagram for the withdrawal state machine.
 * This is a convenience wrapper around the generic StateMachineDiagramGenerator.
 */
fun main(args: Array<String>) {
  if (args.isEmpty()) {
    System.err.println("Usage: StateMachineDiagramGenerator <output-file>")
    exitProcess(1)
  }

  val outputFile = File(args[0])

  val injector = Guice.createInjector(
    TestPersistenceModule(),
    TestModule()
  )

  val stateMachine = injector.getInstance(
    Key.get(object : TypeLiteral<StateMachine<WithdrawalToken, Withdrawal, WithdrawalState>>() {})
  )

  // Use the generic diagram generator from the common module
  StateMachineDiagramGenerator.generateDiagram(stateMachine, CollectingInfo, outputFile)
}
