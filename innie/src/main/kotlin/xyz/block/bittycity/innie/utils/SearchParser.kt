package xyz.block.bittycity.innie.utils

import arrow.core.raise.result
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.innie.models.DepositState
import xyz.block.domainapi.CompareOperator
import xyz.block.domainapi.CompareValue
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.SearchParameter
import java.time.Instant
import java.time.ZoneOffset

class SearchParser {
  data class ParsedFilters(
    val customerId: CustomerId?,
    val from: Instant?,
    val to: Instant?,
    val minAmount: Bitcoins?,
    val maxAmount: Bitcoins?,
    val states: List<DepositState>,
    val targetWalletAddress: String?,
    val paymentToken: String?
  )

  private fun flattenExprs(
    parameter: SearchParameter
  ): Result<List<SearchParameter.ParameterExpression<*>>> = result {
    when (parameter) {
      is SearchParameter.LogicalExpression -> {
        if (parameter is SearchParameter.LogicalExpression.Or) {
          throw DomainApiError.InvalidSearchParameter(parameter)
        }
        parameter.operands.flatMap { flattenExprs(it).bind() }
      }

      is SearchParameter.ParameterExpression<*> -> listOf(parameter)
    }
  }

  private fun findSingleExpr(
    exprs: List<SearchParameter.ParameterExpression<*>>,
    type: SearchParameterType
  ): Result<SearchParameter.ParameterExpression<*>?> = result {
    val matches = exprs.filter { it.parameter == type }
    if (matches.size > 1) {
      throw DomainApiError.InvalidSearchParameter(matches[1])
    }
    matches.firstOrNull()
  }

  fun parseFilters(parameter: SearchParameter): Result<ParsedFilters> = result {
    val exprs = flattenExprs(parameter).bind()

    val customerExpr = findSingleExpr(exprs, SearchParameterType.CUSTOMER_ID).bind()
    val fromExpr = findSingleExpr(exprs, SearchParameterType.CREATED_AT_FROM).bind()
    val toExpr = findSingleExpr(exprs, SearchParameterType.CREATED_AT_TO).bind()
    val minAmountExpr = findSingleExpr(exprs, SearchParameterType.MIN_AMOUNT).bind()
    val maxAmountExpr = findSingleExpr(exprs, SearchParameterType.MAX_AMOUNT).bind()
    val statesExpr = findSingleExpr(exprs, SearchParameterType.STATES).bind()
    val addressExpr = findSingleExpr(exprs, SearchParameterType.DESTINATION_ADDRESS).bind()
    val paymentTokenExpr = findSingleExpr(exprs, SearchParameterType.PAYMENT_TOKEN).bind()

    ParsedFilters(
      customerId = parseCustomerId(customerExpr).bind(),
      from = parseFrom(fromExpr).bind(),
      to = parseTo(toExpr).bind(),
      minAmount = parseMinAmount(minAmountExpr).bind(),
      maxAmount = parseMaxAmount(maxAmountExpr).bind(),
      states = parseStates(statesExpr).bind(),
      targetWalletAddress = parseTargetWalletAddress(addressExpr).bind(),
      paymentToken = parsePaymentToken(paymentTokenExpr).bind()
    )
  }

  private fun parseCustomerId(raw: SearchParameter.ParameterExpression<*>?): Result<CustomerId?> =
    result {
      when (raw) {
        null -> null
        else -> {
          require(
            raw.compareOperator == CompareOperator.EQUALS &&
              raw.values.size == 1 &&
              raw.values[0] is CompareValue.StringValue
          ) { throw DomainApiError.InvalidSearchParameter(raw) }
          CustomerId((raw.values[0] as CompareValue.StringValue).value)
        }
      }
    }

  private fun parseFrom(raw: SearchParameter.ParameterExpression<*>?): Result<Instant?> = result {
    if (raw == null) return Result.success(null)

    require(raw.values.size == 1 && raw.values[0] is CompareValue.DateValue) {
      throw DomainApiError.InvalidSearchParameter(raw)
    }
    val instant = (raw.values[0] as CompareValue.DateValue)
      .value.atStartOfDay(ZoneOffset.UTC).toInstant()

    when (raw.compareOperator) {
      CompareOperator.GREATER_THAN,
      CompareOperator.GREATER_THAN_OR_EQUAL -> instant

      else -> throw DomainApiError.InvalidSearchParameter(raw)
    }
  }

  private fun parseTo(raw: SearchParameter.ParameterExpression<*>?): Result<Instant?> = result {
    if (raw == null) return Result.success(null)

    require(raw.values.size == 1 && raw.values[0] is CompareValue.DateValue) {
      throw DomainApiError.InvalidSearchParameter(raw)
    }
    val instant = (raw.values[0] as CompareValue.DateValue)
      .value.plusDays(1)
      .atStartOfDay(ZoneOffset.UTC)
      .toInstant()

    when (raw.compareOperator) {
      CompareOperator.LESS_THAN,
      CompareOperator.LESS_THAN_OR_EQUAL -> instant

      else -> throw DomainApiError.InvalidSearchParameter(raw)
    }
  }

  private fun parseMinAmount(raw: SearchParameter.ParameterExpression<*>?): Result<Bitcoins?> =
    result {
      if (raw == null) return Result.success(null)

      require(raw.values.size == 1 && raw.values[0] is CompareValue.LongValue) {
        throw DomainApiError.InvalidSearchParameter(raw)
      }
      val amount = Bitcoins((raw.values[0] as CompareValue.LongValue).value)

      when (raw.compareOperator) {
        CompareOperator.GREATER_THAN,
        CompareOperator.GREATER_THAN_OR_EQUAL -> amount

        else -> throw DomainApiError.InvalidSearchParameter(raw)
      }
    }

  private fun parseMaxAmount(raw: SearchParameter.ParameterExpression<*>?): Result<Bitcoins?> =
    result {
      if (raw == null) return Result.success(null)

      require(raw.values.size == 1 && raw.values[0] is CompareValue.LongValue) {
        throw DomainApiError.InvalidSearchParameter(raw)
      }
      val amount = Bitcoins((raw.values[0] as CompareValue.LongValue).value)

      when (raw.compareOperator) {
        CompareOperator.LESS_THAN,
        CompareOperator.LESS_THAN_OR_EQUAL -> amount

        else -> throw DomainApiError.InvalidSearchParameter(raw)
      }
    }

  private fun parseStates(
    raw: SearchParameter.ParameterExpression<*>?
  ): Result<List<DepositState>> = result {
    if (raw == null) return Result.success(emptyList())
    if (raw.compareOperator != CompareOperator.IN) {
      throw DomainApiError.InvalidSearchParameter(raw)
    }

    val allStates = DepositState::class
      .sealedSubclasses
      .mapNotNull { it.objectInstance }

    raw.values
      .filterIsInstance<CompareValue.StringValue>()
      .map { value ->
        allStates.firstOrNull { state ->
          state.name == value.value || state.javaClass.simpleName == value.value
        } ?: throw DomainApiError.InvalidSearchParameter(raw)
      }
  }

  private fun parseTargetWalletAddress(
    raw: SearchParameter.ParameterExpression<*>?
  ): Result<String?> = result {
    if (raw == null) return Result.success(null)

    require(
      raw.compareOperator == CompareOperator.EQUALS &&
        raw.values.size == 1 &&
        raw.values[0] is CompareValue.StringValue
    ) {
      throw DomainApiError.InvalidSearchParameter(raw)
    }

    (raw.values[0] as CompareValue.StringValue).value
  }

  private fun parsePaymentToken(
    raw: SearchParameter.ParameterExpression<*>?
  ): Result<String?> = result {
    if (raw == null) return Result.success(null)

    require(
      raw.compareOperator == CompareOperator.EQUALS &&
        raw.values.size == 1 &&
        raw.values[0] is CompareValue.StringValue
    ) {
      throw DomainApiError.InvalidSearchParameter(raw)
    }

    (raw.values[0] as CompareValue.StringValue).value
  }

  enum class SearchParameterType {
    CUSTOMER_ID,
    CREATED_AT_FROM,
    CREATED_AT_TO,
    MIN_AMOUNT,
    MAX_AMOUNT,
    STATES,
    DESTINATION_ADDRESS,
    PAYMENT_TOKEN
  }
}
