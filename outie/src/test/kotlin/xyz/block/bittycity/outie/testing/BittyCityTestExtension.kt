package xyz.block.bittycity.outie.testing

import com.google.inject.Guice
import com.google.inject.Injector
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class BittyCityTestExtension : BeforeEachCallback {

  companion object {
    private const val INJECTOR_KEY = "bittycity.test.injector"

    private fun getOrCreateInjector(context: ExtensionContext): Injector {
      val store = context.root.getStore(ExtensionContext.Namespace.GLOBAL)
      return store.getOrComputeIfAbsent(INJECTOR_KEY, {
        Guice.createInjector(
          TestModule()
        )
      }, Injector::class.java)
    }
  }

  override fun beforeEach(context: ExtensionContext) {
    val injector = getOrCreateInjector(context)
    val testInstance = context.requiredTestInstance
    injector.injectMembers(testInstance)
  }
}
