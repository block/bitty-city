package xyz.block.bittycity.innie.testing

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Scopes
import com.google.inject.TypeLiteral
import com.squareup.moshi.Moshi
import io.kotest.property.arbitrary.next
import jakarta.inject.Singleton
import xyz.block.bittycity.common.client.CurrencyDisplayPreferenceClient
import xyz.block.bittycity.common.client.EligibilityClient
import xyz.block.bittycity.common.client.RiskClient
import xyz.block.bittycity.common.client.SanctionsClient
import xyz.block.bittycity.innie.client.DepositLedgerClient
import xyz.block.bittycity.innie.models.DepositReversalToken
import xyz.block.bittycity.common.idempotency.IdempotencyOperations
import xyz.block.bittycity.common.store.Transactor
import xyz.block.bittycity.innie.client.MetricsClient
import xyz.block.bittycity.innie.json.DepositMoshi
import xyz.block.bittycity.innie.models.DepositToken
import xyz.block.bittycity.innie.models.RequirementId
import xyz.block.bittycity.innie.store.DepositOperations
import xyz.block.bittycity.innie.store.ResponseOperations

class TestModule : AbstractModule() {

  override fun configure() {
    install(BittyCityTestModule)
    install(TestStateMachineModule)

    bind(TestApp::class.java).`in`(Scopes.SINGLETON)
    bind(TestRunData::class.java).toInstance(Arbitrary.testRunData.next())

    // Bind the concrete generic type that Guice expects
    bind(object : TypeLiteral<IdempotencyOperations<DepositToken, RequirementId>>() {})
      .to(FakeResponseOperations::class.java)
      .`in`(Scopes.SINGLETON)
    bind(FakeResponseOperations::class.java).`in`(Scopes.SINGLETON)
    bind(FakeRiskClient::class.java).`in`(Scopes.SINGLETON)
    bind(object : TypeLiteral<RiskClient<DepositToken>>() {})
      .to(FakeRiskClient::class.java)
      .`in`(Scopes.SINGLETON)
    bind(FakeSanctionsClient::class.java).`in`(Scopes.SINGLETON)
    bind(object : TypeLiteral<SanctionsClient<DepositReversalToken>>() {})
      .to(FakeSanctionsClient::class.java)
      .`in`(Scopes.SINGLETON)
    bind(FakeReversalRiskClient::class.java).`in`(Scopes.SINGLETON)
    bind(object : TypeLiteral<RiskClient<DepositReversalToken>>() {})
      .to(FakeReversalRiskClient::class.java)
      .`in`(Scopes.SINGLETON)

    // Bind fake clients
    bindSingletonFake<CurrencyDisplayPreferenceClient, FakeCurrencyDisplayPreferenceClient>()
    bindSingletonFake<MetricsClient, FakeMetricsClient>()
    bindSingletonFake<EligibilityClient, FakeEligibilityClient>()
    bindSingletonFake<DepositLedgerClient, FakeLedgerClient>()

    // Bind fake operations
    bind(FakeDepositOperations::class.java).`in`(Scopes.SINGLETON)
    bind(DepositOperations::class.java).to(FakeDepositOperations::class.java).`in`(Scopes.SINGLETON)

    bind(Moshi::class.java).toInstance(DepositMoshi.create())
  }

  @Provides
  @Singleton
  fun provideResponseTransactor(
    operations: IdempotencyOperations<DepositToken, RequirementId>
  ): Transactor<ResponseOperations> {
    return FakeResponseTransactor(operations)
  }

  @Provides
  @Singleton
  fun provideDepositTransactor(
    operations: FakeDepositOperations
  ): Transactor<DepositOperations> {
    return FakeDepositTransactor(operations)
  }

  private inline fun <reified A, reified B : A> bindSingletonFake() {
    bind(A::class.java).to(B::class.java).`in`(Scopes.SINGLETON)
    bind(B::class.java).`in`(Scopes.SINGLETON)
  }
}
