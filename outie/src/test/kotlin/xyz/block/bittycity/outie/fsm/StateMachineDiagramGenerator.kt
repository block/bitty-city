package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.StateMachine
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.outie.models.Withdrawal
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import xyz.block.bittycity.outie.store.TestPersistenceModule
import xyz.block.bittycity.outie.testing.TestModule
import java.io.File

fun main(args: Array<String>) {
  if (args.isEmpty()) {
    System.err.println("Usage: StateMachineDiagramGenerator <output-file>")
    System.exit(1)
  }

  val outputFile = File(args[0])
  
  val injector = Guice.createInjector(
    TestPersistenceModule(),
    TestModule()
  )
  
  val stateMachine = injector.getInstance(
    Key.get(object : TypeLiteral<StateMachine<WithdrawalToken, Withdrawal, WithdrawalState>>() {})
  )
  
  outputFile.parentFile?.mkdirs()
  val diagram = stateMachine.mermaidStateDiagramMarkdown(CollectingInfo)
  outputFile.writeText("```mermaid\n$diagram\n```")
  
  println("State machine diagram generated at: ${outputFile.absolutePath}")
}
