package xyz.block.bittycity.innie.fsm

import app.cash.kfsm.StateMachine
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import kotlin.system.exitProcess
import xyz.block.bittycity.common.fsm.StateMachineDiagramGenerator
import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.WaitingForDepositPendingConfirmationStatus
import xyz.block.bittycity.innie.testing.TestModule
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
    TestModule()
  )

  val stateMachine = injector.getInstance(
    Key.get(object : TypeLiteral<StateMachine<DepositToken, Deposit, DepositState>>() {})
  )

  // Use the generic diagram generator from the common module
  StateMachineDiagramGenerator.generateDiagram(stateMachine, WaitingForDepositPendingConfirmationStatus, outputFile)
}
