package xyz.block.bittycity.outie.utils

import arrow.core.raise.result
import xyz.block.bittycity.outie.models.Bitcoins
import xyz.block.bittycity.outie.models.CustomerId
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.domainapi.CompareOperator
import xyz.block.domainapi.CompareValue
import xyz.block.domainapi.DomainApiError
import xyz.block.domainapi.SearchParameter
import java.time.Instant
import java.time.ZoneOffset

class SearchParser {
  /** Immutable holder for all supported filters. */
  data class ParsedFilters(
    val customerId: CustomerId?,
    val from: Instant?,
    val to: Instant?,
    val minAmount: Bitcoins?,
    val maxAmount: Bitcoins?,
    val states: List<WithdrawalState>,
    val destinationAddress: String?
  )

  /** Flatten any AND/OR into a flat list of ParameterExpression<*>. */
  private fun flattenExprs(
    param: SearchParameter
  ): Result<List<SearchParameter.ParameterExpression<*>>> = result {
    when (param) {
      is SearchParameter.LogicalExpression -> {
        if (param is SearchParameter.LogicalExpression.Or) {
          // We don't support OR searches for now.
          throw DomainApiError.InvalidSearchParameter(param)
        }

        param.operands.flatMap { flattenExprs(it).bind() }
      }

      is SearchParameter.ParameterExpression<*> ->
        listOf(param)
    }
  }

  /** Helper to find exactly one expression of a given type or throw if >1 */
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

  /** Top-level entry point: find each expr by type, then delegate. */
  fun parseFilters(parameter: SearchParameter): Result<ParsedFilters> = result {
    val exprs = flattenExprs(parameter).bind()

    val custExpr = findSingleExpr(exprs, SearchParameterType.CUSTOMER_ID)
    val fromExpr = findSingleExpr(exprs, SearchParameterType.CREATED_AT_FROM)
    val toExpr = findSingleExpr(exprs, SearchParameterType.CREATED_AT_TO)
    val minExpr = findSingleExpr(exprs, SearchParameterType.MIN_AMOUNT)
    val maxExpr = findSingleExpr(exprs, SearchParameterType.MAX_AMOUNT)
    val stExpr = findSingleExpr(exprs, SearchParameterType.STATES)
    val dstExpr = findSingleExpr(exprs, SearchParameterType.DESTINATION_ADDRESS)

    ParsedFilters(
      customerId = parseCustomerId(custExpr.bind()).bind(),
      from = parseFrom(fromExpr.bind()).bind(),
      to = parseTo(toExpr.bind()).bind(),
      minAmount = parseMinAmount(minExpr.bind()).bind(),
      maxAmount = parseMaxAmount(maxExpr.bind()).bind(),
      states = parseStates(stExpr.bind()).bind(),
      destinationAddress = parseDestinationAddress(dstExpr.bind()).bind()
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
    val inst = (raw.values[0] as CompareValue.DateValue)
      .value.atStartOfDay(ZoneOffset.UTC).toInstant()

    when (raw.compareOperator) {
      CompareOperator.GREATER_THAN,
      CompareOperator.GREATER_THAN_OR_EQUAL -> inst

      else -> throw DomainApiError.InvalidSearchParameter(raw)
    }
  }

  private fun parseTo(raw: SearchParameter.ParameterExpression<*>?): Result<Instant?> = result {
    if (raw == null) return Result.success(null)

    require(raw.values.size == 1 && raw.values[0] is CompareValue.DateValue) {
      throw DomainApiError.InvalidSearchParameter(raw)
    }
    val inst = (raw.values[0] as CompareValue.DateValue)
      .value.plusDays(1)
      .atStartOfDay(ZoneOffset.UTC)
      .toInstant()

    when (raw.compareOperator) {
      CompareOperator.LESS_THAN,
      CompareOperator.LESS_THAN_OR_EQUAL -> inst

      else -> throw DomainApiError.InvalidSearchParameter(raw)
    }
  }

  private fun parseMinAmount(raw: SearchParameter.ParameterExpression<*>?): Result<Bitcoins?> =
    result {
      if (raw == null) return Result.success(null)

      require(raw.values.size == 1 && raw.values[0] is CompareValue.LongValue) {
        raise(DomainApiError.InvalidSearchParameter(raw))
      }
      val sat = Bitcoins((raw.values[0] as CompareValue.LongValue).value)

      when (raw.compareOperator) {
        CompareOperator.GREATER_THAN,
        CompareOperator.GREATER_THAN_OR_EQUAL -> sat

        else -> throw DomainApiError.InvalidSearchParameter(raw)
      }
    }

  private fun parseMaxAmount(raw: SearchParameter.ParameterExpression<*>?): Result<Bitcoins?> =
    result {
      if (raw == null) return Result.success(null)

      require(raw.values.size == 1 && raw.values[0] is CompareValue.LongValue) {
        throw DomainApiError.InvalidSearchParameter(raw)
      }
      val sat = Bitcoins((raw.values[0] as CompareValue.LongValue).value)

      when (raw.compareOperator) {
        CompareOperator.LESS_THAN,
        CompareOperator.LESS_THAN_OR_EQUAL -> sat

        else -> throw DomainApiError.InvalidSearchParameter(raw)
      }
    }

  private fun parseStates(
    raw: SearchParameter.ParameterExpression<*>?
  ): Result<List<WithdrawalState>> = result {
    if (raw == null) return Result.success(listOf())

    if (raw.compareOperator != CompareOperator.IN) {
      throw DomainApiError.InvalidSearchParameter(raw)
    }
    val allStates = WithdrawalState::class
      .sealedSubclasses
      .mapNotNull { it.objectInstance }

    raw.values
      .filterIsInstance<CompareValue.StringValue>()
      .map { sv ->
        allStates.firstOrNull { st ->
          st.name == sv.value || st.javaClass.simpleName == sv.value
        } ?: throw DomainApiError.InvalidSearchParameter(raw)
      }
  }

  private fun parseDestinationAddress(
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
    DESTINATION_ADDRESS
  }
}
