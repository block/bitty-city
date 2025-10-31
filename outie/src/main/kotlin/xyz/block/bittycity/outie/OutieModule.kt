package xyz.block.bittycity.outie

import com.google.inject.AbstractModule
import xyz.block.bittycity.outie.controllers.DomainControllerModule
import xyz.block.bittycity.outie.fsm.StateMachineModule
import xyz.block.bittycity.outie.models.Bitcoins
import xyz.block.bittycity.outie.validation.ValidationModule

/**
 * Module for configuring the bitty-lib components.
 */
open class OutieModule : AbstractModule() {

    override fun configure() {
        installValidationModule()
        install(StateMachineModule)
        install(DomainControllerModule)
        Bitcoins.currency // register the currency
    }

    open fun installValidationModule() {
        install(ValidationModule)
    }
}
