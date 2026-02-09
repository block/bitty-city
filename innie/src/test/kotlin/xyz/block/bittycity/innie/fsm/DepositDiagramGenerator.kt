package xyz.block.bittycity.innie.fsm

import xyz.block.bittycity.innie.models.Deposit
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.New
import java.io.File
import kotlin.system.exitProcess

/**
 * Generates a Mermaid state machine diagram for the deposit state machine.
 * This is a convenience wrapper around the generic StateMachineDiagramGenerator.
 */
fun main(args: Array<String>) {
  if (args.isEmpty()) {
    System.err.println("Usage: DepositDiagramGenerator <output-file>")
    exitProcess(1)
  }

  val outputFile = File(args[0])

  StateMachineDiagramGenerator.generateDiagram<DepositToken, Deposit, DepositState>(New, outputFile)
}
