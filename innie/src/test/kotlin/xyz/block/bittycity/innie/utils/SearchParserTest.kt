package xyz.block.bittycity.innie.utils

import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import xyz.block.domainapi.CompareOperator
import xyz.block.domainapi.CompareValue
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.SearchParameter

class SearchParserTest {
  private val subject = SearchParser()

  @Test
  fun `parseFilters parses payment token`() {
    val paymentToken = "pay-token-1"
    val parameter = SearchParameter.ParameterExpression(
      parameter = SearchParser.SearchParameterType.PAYMENT_TOKEN,
      compareOperator = CompareOperator.EQUALS,
      values = listOf(CompareValue.StringValue(paymentToken))
    )

    subject.parseFilters(parameter) shouldBeSuccess {
      it.paymentToken shouldBe paymentToken
    }
  }

  @Test
  fun `parseFilters rejects invalid payment token operator`() {
    val parameter = SearchParameter.ParameterExpression(
      parameter = SearchParser.SearchParameterType.PAYMENT_TOKEN,
      compareOperator = CompareOperator.IN,
      values = listOf(CompareValue.StringValue("pay-token-1"))
    )

    subject.parseFilters(parameter) shouldBeFailure {
      it::class.java shouldBe DomainApiError.InvalidSearchParameter::class.java
    }
  }
}
