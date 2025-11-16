package xyz.block.bittycity.outie.client

import xyz.block.domainapi.InfoOnly

/**
 * Interface for a service that determines if a user is eligible to use a Bitcoin product.
 */
interface EligibilityClient {

  /**
   * Determines if a user is eligible to use a Bitcoin product.
   *
   * @param customerId The user's ID.
   *
   * @return [Eligibility] indicating if the user is eligible for the product.
   */
  fun productEligibility(customerId: String): Result<Eligibility>
}

/**
 * Represents the eligibility of a user to use a Bitcoin product.
 */
sealed class Eligibility {
  /**
   * The user is ineligible to use the product.
   *
   * @param violations The reasons the user is ineligible. Can be empty if the reasons are not known.
   */
  data class Ineligible(val violations: List<String>) : Eligibility()

  /**
   * The user is eligible to use the product.
   *
   * @param allowReasons The reasons the user is eligible. Can be empty if the reasons are not known.
   */
  data class Eligible(val allowReasons: List<String>) : Eligibility()
}



class IneligibleCustomer(val violations: List<String>) : Exception(), InfoOnly
