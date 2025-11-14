package xyz.block.bittycity.innie

import com.google.inject.AbstractModule
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.innie.fsm.StateMachineModule

open class InnieModule  : AbstractModule() {

  override fun configure() {
    installValidationModule()
    install(StateMachineModule)
    Bitcoins.currency
  }

  open fun installValidationModule() {

  }
}
