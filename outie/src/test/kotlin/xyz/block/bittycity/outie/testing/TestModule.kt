package xyz.block.bittycity.outie.testing

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Scopes
import com.google.inject.name.Names
import xyz.block.bittycity.outie.client.BitcoinAccountClient
import xyz.block.bittycity.outie.client.CurrencyDisplayPreferenceClient
import xyz.block.bittycity.outie.client.EligibilityClient
import xyz.block.bittycity.outie.client.EventClient
import xyz.block.bittycity.outie.client.ExchangeRateClient
import xyz.block.bittycity.outie.client.FeeQuoteClient
import xyz.block.bittycity.outie.client.LedgerClient
import xyz.block.bittycity.outie.client.LimitClient
import xyz.block.bittycity.outie.client.MetricsClient
import xyz.block.bittycity.outie.client.OnChainClient
import xyz.block.bittycity.outie.client.PreFlightClient
import xyz.block.bittycity.outie.client.RiskClient
import xyz.block.bittycity.outie.client.SanctionsClient
import xyz.block.bittycity.outie.client.TravelRuleClient
import xyz.block.bittycity.outie.json.WithdrawalMoshi
import com.squareup.moshi.Moshi
import io.kotest.property.arbitrary.next
import jakarta.inject.Named
import java.time.Clock

class TestModule : AbstractModule() {

  override fun configure() {
    install(BittyCityTestModule)
    bind(TestApp::class.java).`in`(Scopes.SINGLETON)
    bind(TestRunData::class.java).toInstance(Arbitrary.testRunData.next())
    bind(Clock::class.java).toInstance(TestClock())

    bindSingletonFake<BitcoinAccountClient, FakeBitcoinAccountClient>()
    bindSingletonFake<CurrencyDisplayPreferenceClient, FakeCurrencyDisplayPreferenceClient>()
    bindSingletonFake<EventClient, FakeEventClient>()
    bindSingletonFake<ExchangeRateClient, FakeExchangeRateClient>()
    bindSingletonFake<FeeQuoteClient, FakeFeeQuoteClient>()
    bindSingletonFake<LedgerClient, FakeLedgerClient>()
    bindSingletonFake<LimitClient, FakeLimitClient>()
    bindSingletonFake<OnChainClient, FakeOnChainClient>()
    bindSingletonFake<RiskClient, FakeRiskClient>()
    bindSingletonFake<SanctionsClient, FakeSanctionsClient>()
    bindSingletonFake<TravelRuleClient, FakeTravelRuleClient>()
    bindSingletonFake<EligibilityClient, FakeEligibilityClient>()
    bindSingletonFake<PreFlightClient, FakePreFlightClient>()
    bindSingletonFake<MetricsClient, FakeMetricsClient>()

    bind(String::class.java).annotatedWith(Names.named("withdrawal.sanctions.freeze_to"))
      .toInstance("freezeToAccount")
    bind(String::class.java).annotatedWith(Names.named("withdrawal.sanctions.balance_token"))
      .toInstance("freezeToBalance")
    bind(Long::class.java).annotatedWith(Names.named("withdrawal.amounts.minimum")).toInstance(5_000L)
    bind(Long::class.java).annotatedWith(Names.named("withdrawal.amounts.free_tier_minimum"))
      .toInstance(100_000L)
    bind(Long::class.java).annotatedWith(Names.named("withdrawal.stuck_after_minutes")).toInstance(60L)
    bind(Long::class.java).annotatedWith(Names.named("withdrawal.retryable_stuck_after_minutes")).toInstance(5L)

    bind(Moshi::class.java).toInstance(WithdrawalMoshi.create())

    @Provides
    @Named("withdrawal.supported_countries")
    fun supportedCountries(): List<String> = listOf("US")
  }

  private inline fun <reified A, reified B : A> bindSingletonFake() {
    bind(A::class.java).to(B::class.java).`in`(Scopes.SINGLETON)
    bind(B::class.java).`in`(Scopes.SINGLETON)
  }
}
