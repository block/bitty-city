package xyz.block.bittycity.outie.utils

import xyz.block.bittycity.outie.models.Bitcoins
import xyz.block.bittycity.outie.models.CollectingInfo
import xyz.block.bittycity.outie.models.CustomerId
import xyz.block.bittycity.outie.models.SubmittingOnChain
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import xyz.block.domainapi.CompareOperator
import xyz.block.domainapi.CompareValue
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.SearchParameter
import java.time.LocalDate
import java.time.ZoneOffset

class SearchParserTest {
  private val parser = SearchParser()

  @Test
  fun `parseFilters with only customer ID returns minimal filters`() {
    val customerId = "CUST123"
    val param = SearchParameter.ParameterExpression(
      parameter = SearchParser.SearchParameterType.CUSTOMER_ID,
      compareOperator = CompareOperator.EQUALS,
      values = listOf(CompareValue.StringValue(customerId))
    )

    val result = parser.parseFilters(param)

    result shouldBeSuccess {
      it shouldBe SearchParser.ParsedFilters(
        customerId = CustomerId(customerId),
        from = null,
        to = null,
        minAmount = null,
        maxAmount = null,
        states = emptyList(),
        destinationAddress = null
      )
    }
  }

  @Test
  fun `parseFilters with multiple customer IDs throws error`() {
    val param = SearchParameter.LogicalExpression.And(
      listOf(
        SearchParameter.ParameterExpression(
          parameter = SearchParser.SearchParameterType.CUSTOMER_ID,
          compareOperator = CompareOperator.EQUALS,
          values = listOf(CompareValue.StringValue("CUST1"))
        ),
        SearchParameter.ParameterExpression(
          parameter = SearchParser.SearchParameterType.CUSTOMER_ID,
          compareOperator = CompareOperator.EQUALS,
          values = listOf(CompareValue.StringValue("CUST2"))
        )
      )
    )

    parser.parseFilters(param) shouldBeFailure {
      it::class.java shouldBe DomainApiError.InvalidSearchParameter::class.java
    }
  }

  @Test
  fun `parseFilters with date range`() {
    val customerId = "CUST123"
    val fromDate = LocalDate.of(2025, 1, 1)
    val toDate = LocalDate.of(2025, 1, 31)

    val param = SearchParameter.LogicalExpression.And(
      listOf(
        SearchParameter.ParameterExpression(
          parameter = SearchParser.SearchParameterType.CUSTOMER_ID,
          compareOperator = CompareOperator.EQUALS,
          values = listOf(CompareValue.StringValue(customerId))
        ),
        SearchParameter.ParameterExpression(
          parameter = SearchParser.SearchParameterType.CREATED_AT_FROM,
          compareOperator = CompareOperator.GREATER_THAN_OR_EQUAL,
          values = listOf(CompareValue.DateValue(fromDate))
        ),
        SearchParameter.ParameterExpression(
          parameter = SearchParser.SearchParameterType.CREATED_AT_TO,
          compareOperator = CompareOperator.LESS_THAN,
          values = listOf(CompareValue.DateValue(toDate))
        )
      )
    )

    parser.parseFilters(param) shouldBeSuccess {
      it shouldBe SearchParser.ParsedFilters(
        customerId = CustomerId(customerId),
        from = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
        to = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
        minAmount = null,
        maxAmount = null,
        states = emptyList(),
        destinationAddress = null
      )
    }
  }

  @Test
  fun `parseFilters with amount range`() {
    val customerId = "CUST123"
    val minAmount = 1000L
    val maxAmount = 5000L

    val param = SearchParameter.LogicalExpression.And(
      listOf(
        SearchParameter.ParameterExpression(
          parameter = SearchParser.SearchParameterType.CUSTOMER_ID,
          compareOperator = CompareOperator.EQUALS,
          values = listOf(CompareValue.StringValue(customerId))
        ),
        SearchParameter.ParameterExpression(
          parameter = SearchParser.SearchParameterType.MIN_AMOUNT,
          compareOperator = CompareOperator.GREATER_THAN_OR_EQUAL,
          values = listOf(CompareValue.LongValue(minAmount))
        ),
        SearchParameter.ParameterExpression(
          parameter = SearchParser.SearchParameterType.MAX_AMOUNT,
          compareOperator = CompareOperator.LESS_THAN_OR_EQUAL,
          values = listOf(CompareValue.LongValue(maxAmount))
        )
      )
    )

    parser.parseFilters(param) shouldBeSuccess {
      it shouldBe SearchParser.ParsedFilters(
        customerId = CustomerId(customerId),
        from = null,
        to = null,
        minAmount = Bitcoins(minAmount),
        maxAmount = Bitcoins(maxAmount),
        states = emptyList(),
        destinationAddress = null
      )
    }
  }

  @Test
  fun `parseFilters with states`() {
    val customerId = "CUST123"
    val stateNames = listOf(CollectingInfo.name, SubmittingOnChain.name)

    val param = SearchParameter.LogicalExpression.And(
      listOf(
        SearchParameter.ParameterExpression(
          parameter = SearchParser.SearchParameterType.CUSTOMER_ID,
          compareOperator = CompareOperator.EQUALS,
          values = listOf(CompareValue.StringValue(customerId))
        ),
        SearchParameter.ParameterExpression(
          parameter = SearchParser.SearchParameterType.STATES,
          compareOperator = CompareOperator.IN,
          values = stateNames.map { CompareValue.StringValue(it) }
        )
      )
    )

    parser.parseFilters(param) shouldBeSuccess {
      it.customerId shouldBe CustomerId(customerId)
      it.states.toSet() shouldBe setOf(CollectingInfo, SubmittingOnChain)
    }
  }

  @Test
  fun `parseFilters errors when using OR`() {
    val customerId = "CUST123"
    val stateNames = listOf("NEW", "SUBMITTED")

    val param = SearchParameter.LogicalExpression.Or(
      listOf(
        SearchParameter.ParameterExpression(
          parameter = SearchParser.SearchParameterType.CUSTOMER_ID,
          compareOperator = CompareOperator.EQUALS,
          values = listOf(CompareValue.StringValue(customerId))
        ),
        SearchParameter.ParameterExpression(
          parameter = SearchParser.SearchParameterType.STATES,
          compareOperator = CompareOperator.IN,
          values = stateNames.map { CompareValue.StringValue(it) }
        )
      )
    )

    parser.parseFilters(param) shouldBeFailure {
      it::class.java shouldBe DomainApiError.InvalidSearchParameter::class.java
    }
  }

  @Test
  fun `parseFilters with destination address`() {
    val customerId = "CUST123"
    val address = "bc1qxyz..."

    val param = SearchParameter.LogicalExpression.And(
      listOf(
        SearchParameter.ParameterExpression(
          parameter = SearchParser.SearchParameterType.CUSTOMER_ID,
          compareOperator = CompareOperator.EQUALS,
          values = listOf(CompareValue.StringValue(customerId))
        ),
        SearchParameter.ParameterExpression(
          parameter = SearchParser.SearchParameterType.DESTINATION_ADDRESS,
          compareOperator = CompareOperator.EQUALS,
          values = listOf(CompareValue.StringValue(address))
        )
      )
    )

    parser.parseFilters(param) shouldBeSuccess {
      it shouldBe SearchParser.ParsedFilters(
        customerId = CustomerId(customerId),
        from = null,
        to = null,
        minAmount = null,
        maxAmount = null,
        states = emptyList(),
        destinationAddress = address
      )
    }
  }

  @Test
  fun `parseFilters with invalid state throws error`() {
    val customerId = "CUST123"
    val invalidState = "INVALID_STATE"

    val param = SearchParameter.LogicalExpression.And(
      listOf(
        SearchParameter.ParameterExpression(
          parameter = SearchParser.SearchParameterType.CUSTOMER_ID,
          compareOperator = CompareOperator.EQUALS,
          values = listOf(CompareValue.StringValue(customerId))
        ),
        SearchParameter.ParameterExpression(
          parameter = SearchParser.SearchParameterType.STATES,
          compareOperator = CompareOperator.IN,
          values = listOf(CompareValue.StringValue(invalidState))
        )
      )
    )

    parser.parseFilters(param) shouldBeFailure {
      it.javaClass shouldBe DomainApiError.InvalidSearchParameter::class.java
    }
  }

  @Test
  fun `parseFilters with invalid operator for date throws error`() {
    val customerId = "CUST123"
    val date = LocalDate.now()

    val param = SearchParameter.LogicalExpression.And(
      listOf(
        SearchParameter.ParameterExpression(
          parameter = SearchParser.SearchParameterType.CUSTOMER_ID,
          compareOperator = CompareOperator.EQUALS,
          values = listOf(CompareValue.StringValue(customerId))
        ),
        SearchParameter.ParameterExpression(
          parameter = SearchParser.SearchParameterType.CREATED_AT_FROM,
          compareOperator = CompareOperator.EQUALS, // Invalid operator for date range
          values = listOf(CompareValue.DateValue(date))
        )
      )
    )

    parser.parseFilters(param) shouldBeFailure {
      it.javaClass shouldBe DomainApiError.InvalidSearchParameter::class.java
    }
  }
}
