package xyz.block.bittycity.innie.testing

import com.google.inject.Guice
import com.google.inject.Injector
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class BittyCityTestExtension : BeforeEachCallback, AfterAllCallback {

  companion object {
    private const val INJECTOR_KEY = "bittycity.test.injector"

    /**
     * Get or create the Guice injector for the test class.
     * The injector is created once per test class and stored in the ExtensionContext.
     */
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
    // Get or create the injector
    val injector = getOrCreateInjector(context)

    // Inject dependencies into the test instance
    val testInstance = context.requiredTestInstance
    injector.injectMembers(testInstance)
  }

  override fun afterAll(context: ExtensionContext) {
    // Testcontainers cleanup is handled automatically via the Ryuk container
    // which removes containers when the JVM exits.
    // The container is reused across all test classes for better performance.
  }
}
