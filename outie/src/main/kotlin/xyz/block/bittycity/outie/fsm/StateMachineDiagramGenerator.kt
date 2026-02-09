package xyz.block.bittycity.outie.fsm

import app.cash.kfsm.State
import app.cash.kfsm.StateMachine
import app.cash.kfsm.Value
import java.io.File

/**
 * Generic utility for generating Mermaid state machine diagrams from kfsm StateMachines.
 *
 * This utility can be used with any state machine that follows the kfsm pattern.
 *
 * ## Example Usage
 *
 * ```kotlin
 * // In your test or build script:
 * val injector = Guice.createInjector(YourModule())
 * val stateMachine = injector.getInstance(
 *   Key.get(object : TypeLiteral<StateMachine<YourIdType, YourValueType, YourStateType>>() {})
 * )
 *
 * // Generate the diagram
 * StateMachineDiagramGenerator.generateDiagram(
 *   stateMachine = stateMachine,
 *   initialState = YourInitialState,
 *   outputFile = File("docs/state-machine.md")
 * )
 * ```
 *
 * ## Gradle Task Example
 *
 * ```kotlin
 * tasks.register<JavaExec>("generateStateMachineDiagram") {
 *   group = "documentation"
 *   description = "Generate state machine diagram"
 *   classpath = sourceSets.test.get().runtimeClasspath
 *   mainClass.set("your.package.DiagramGeneratorKt")
 *   args(project.file("docs/state-machine.md").absolutePath)
 * }
 * ```
 */
object StateMachineDiagramGenerator {

  /**
   * Generates a Mermaid state diagram markdown file for the given state machine.
   *
   * @param stateMachine The state machine to generate a diagram for
   * @param initialState The initial state to start the diagram from
   * @param outputFile The file to write the diagram to
   */
  fun <ID : Any, V : Value<ID, V, S>, S : State<ID, V, S>> generateDiagram(
    stateMachine: StateMachine<ID, V, S>,
    initialState: S,
    outputFile: File
  ) {
    outputFile.parentFile?.mkdirs()
    val diagram = stateMachine.mermaidStateDiagramMarkdown(initialState)
    outputFile.writeText("```mermaid\n$diagram\n```")

    println("State machine diagram generated at: ${outputFile.absolutePath}")
  }

  /**
   * Generates a Mermaid state diagram markdown string for the given state machine.
   *
   * @param stateMachine The state machine to generate a diagram for
   * @param initialState The initial state to start the diagram from
   * @return The Mermaid diagram as a markdown string
   */
  fun <ID : Any, V : Value<ID, V, S>, S : State<ID, V, S>> generateDiagramString(
    stateMachine: StateMachine<ID, V, S>,
    initialState: S
  ): String {
    val diagram = stateMachine.mermaidStateDiagramMarkdown(initialState)
    return "```mermaid\n$diagram\n```"
  }
}
