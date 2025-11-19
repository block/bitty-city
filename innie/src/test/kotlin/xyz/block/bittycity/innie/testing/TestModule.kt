package xyz.block.bittycity.innie.testing

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Scopes
import com.google.inject.TypeLiteral
import com.squareup.moshi.Moshi
import jakarta.inject.Singleton
import xyz.block.bittycity.common.idempotency.IdempotencyOperations
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.innie.json.DepositMoshi
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.store.ResponseOperations

class TestModule : AbstractModule() {

  override fun configure() {
    install(BittyCityTestModule)

    // Bind the concrete generic type that Guice expects
    bind(object : TypeLiteral<IdempotencyOperations<DepositToken, RequirementId>>() {})
      .to(FakeResponseOperations::class.java)
      .`in`(Scopes.SINGLETON)
    bind(FakeResponseOperations::class.java).`in`(Scopes.SINGLETON)

    bind(Moshi::class.java).toInstance(DepositMoshi.create())
  }

  @Provides
  @Singleton
  fun provideResponseTransactor(
    operations: IdempotencyOperations<DepositToken, RequirementId>
  ): Transactor<ResponseOperations> {
    return FakeResponseTransactor(operations)
  }

  private inline fun <reified A, reified B : A> bindSingletonFake() {
    bind(A::class.java).to(B::class.java).`in`(Scopes.SINGLETON)
    bind(B::class.java).`in`(Scopes.SINGLETON)
  }
}
