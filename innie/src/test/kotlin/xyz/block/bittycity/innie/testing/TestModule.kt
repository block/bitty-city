package xyz.block.bittycity.innie.testing

import com.google.inject.AbstractModule
import com.google.inject.Scopes

class TestModule : AbstractModule() {

  override fun configure() {
    install(BittyCityTestModule)
  }

  private inline fun <reified A, reified B : A> bindSingletonFake() {
    bind(A::class.java).to(B::class.java).`in`(Scopes.SINGLETON)
    bind(B::class.java).`in`(Scopes.SINGLETON)
  }
}
