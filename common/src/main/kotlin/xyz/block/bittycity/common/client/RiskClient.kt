package xyz.block.bittycity.common.client

import xyz.block.bittycity.common.models.CustomerId
import xyz.block.domainapi.InfoOnly

/**
 * Interface for a service that determines if a transaction should be blocked due to Risk violations.
 *
 * @param T The type of the transaction identifier (e.g., WithdrawalToken, DepositToken).
 */
interface RiskClient<T> {
  /**
   * Evaluate transaction against Risk systems.
   *
   * @param customerId The user's ID.
   * @param token The ID of the transaction.
   * @return [RiskEvaluation] indicating the result from risk evaluation.
   */
  fun evaluateRisk(customerId: CustomerId, token: T): Result<RiskEvaluation>
}

/**
 * Represents the result of the transaction's evaluation against risk systems.
 */
sealed class RiskEvaluation {
  /**
   * The transaction did not fail any risk checks.
   */
  data object Checked : RiskEvaluation()

  /**
   * The transaction is evaluated as a scam.
   *
   * @param violations The reasons the transaction failed risk evaluations. Can be empty if the reasons are not known.
   */
  data class ActiveScamWarning(val violations: List<String> = emptyList()) : RiskEvaluation()

  /**
   * The transaction is blocked due to risk violations.
   *
   * @param violations The reasons the transaction failed risk evaluations. Can be empty if the reasons are not known.
   */
  data class Blocked(val violations: List<String> = emptyList()) : RiskEvaluation()
}

data object RiskBlocked : Exception(), InfoOnly
