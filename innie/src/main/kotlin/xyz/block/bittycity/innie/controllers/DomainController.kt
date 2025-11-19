package xyz.block.bittycity.innie.controllers

import app.cash.kfsm.State
import app.cash.kfsm.Value
import app.cash.quiver.extensions.toResult
import org.slf4j.LoggerFactory
import xyz.block.domainapi.util.Controller
import xyz.block.domainapi.util.ProcessAdvancer

open class DomainController <ID, S : State<ID, V, S>, V : Value<ID, V, S>, R>(
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
