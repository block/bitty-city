package xyz.block.bittycity.outie.testing

import com.google.inject.Guice
import com.google.inject.Injector
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import xyz.block.bittycity.outie.store.TestPersistenceModule

/**
 * This manages the test lifecycle including:
 * - Dependency injection setup
 * - Database table truncation before each test
 * - Test instance injection
 */
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
          TestPersistenceModule(),
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

    // Truncate database tables to ensure a clean state for each test
    TestDatabase.truncateTables()
  }

  override fun afterAll(context: ExtensionContext) {
    // Testcontainers cleanup is handled automatically via the Ryuk container
    // which removes containers when the JVM exits.
    // The container is reused across all test classes for better performance.
    // If explicit cleanup is needed, uncomment the line below:
    // TestDatabase.shutdown()
  }
}
