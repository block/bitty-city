package xyz.block.bittycity.outie.client

import xyz.block.bittycity.outie.models.CustomerId
import xyz.block.bittycity.outie.models.WithdrawalToken

/**
 * Interface for a service that determines if a withdrawal should be blocked due to Risk violations.
 */
interface RiskClient {
  /**
   * Evaluate withdrawal against Risk systems.
   *
   * @param customerId The user's ID.
   * @param withdrawalToken The ID of the withdrawal.
   * @return [RiskEvaluation] indicating the result from risk evaluation.
   */
  fun evaluateRisk(customerId: CustomerId, withdrawalToken: WithdrawalToken): Result<RiskEvaluation>
}

/**
 * Represents the result of the withdrawal's evaluation against risk systems.
 */
sealed class RiskEvaluation {
  /**
   * The withdrawal did not fail any risk checks.
   */
  data object Checked : RiskEvaluation()

  /**
   * The withdrawal is evaluated as a scam.
   *
   * @param violations The reasons the withdrawal failed risk evaluations. Can be empty if the reasons are not known.
   */
  data class ActiveScamWarning(val violations: List<String> = emptyList()) : RiskEvaluation()

  /**
   * The withdrawal is blocked due to risk violations.
   *
   * @param violations The reasons the withdrawal failed risk evaluations. Can be empty if the reasons are not known.
   */
  data class Blocked(val violations: List<String> = emptyList()) : RiskEvaluation()
}
