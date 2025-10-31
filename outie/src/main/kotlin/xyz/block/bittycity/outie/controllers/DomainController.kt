package xyz.block.bittycity.outie.controllers

import app.cash.kfsm.State
import app.cash.kfsm.Value
import app.cash.quiver.extensions.toResult
import org.slf4j.LoggerFactory
import xyz.block.domainapi.util.Controller
import xyz.block.domainapi.util.ProcessAdvancer

/**
 * A process advancer that delegates to the appropriate controller based on the state.
 * This controller is used to route requests to the correct controller based on the current state of the process.
 */
open class DomainController<ID, S : State<ID, V, S>, V : Value<ID, V, S>, R>(
  private val controllerMap: Map<S, Controller<ID, S, V, R>>
) : ProcessAdvancer<ID, S, V, R>() {

  private val logger = LoggerFactory.getLogger(DomainController::class.java)

  override fun getController(instance: V): Result<Controller<ID, S, V, R>> =
    controllerMap[instance.state].toResult {
      IllegalArgumentException("No controller found for state: ${instance.state}")
    }.onSuccess {
      logger.info(
        "Delegating to controller: ${it.javaClass.simpleName} for state: ${instance.state}"
      )
    }

  override fun onExecuteFailure(instance: V, error: Throwable) {
    logger.info("Something went wrong. [instance=${instance.id}]", error)
  }
}
