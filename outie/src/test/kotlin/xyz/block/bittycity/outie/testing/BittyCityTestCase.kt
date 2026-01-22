@file:Suppress("DEPRECATION")

package xyz.block.bittycity.outie.testing

import jakarta.inject.Inject
import org.junit.jupiter.api.extension.ExtendWith
import xyz.block.bittycity.outie.testing.fakes.FakeResponseOperations
import xyz.block.bittycity.outie.testing.fakes.FakeWithdrawalOperations

@ExtendWith(BittyCityTestExtension::class)
abstract class BittyCityTestCase {
  @Inject lateinit var app: TestApp
  @Inject lateinit var fakeWithdrawalOperations: FakeWithdrawalOperations
  @Inject lateinit var fakeResponseOperations: FakeResponseOperations

  fun runTest(assertions: TestApp.(TestApp) -> Unit) {
    app.resetFakes()
    app.assertions(app)
  }

  fun setup(configWork: TestApp.(TestApp) -> Unit) {
    app.configWork(app)
  }
}
