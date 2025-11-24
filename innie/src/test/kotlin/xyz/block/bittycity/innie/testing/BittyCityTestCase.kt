package xyz.block.bittycity.innie.testing

import jakarta.inject.Inject
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(BittyCityTestExtension::class)
abstract class BittyCityTestCase {
  @Inject lateinit var app: TestApp

  fun runTest(assertions: TestApp.(TestApp) -> Unit) {
    app.resetFakes()
    app.assertions(app)
  }

  fun setup(configWork: TestApp.(TestApp) -> Unit) {
    app.configWork(app)
  }
}
