package xyz.block.bittycity.innie.validation

import xyz.block.bittycity.common.models.CustomerId
import xyz.block.domainapi.InfoOnly

sealed class ValidationError :
  Exception(),
  InfoOnly

data class ParameterIsRequired(val customerId: CustomerId, val parameter: String) :
  ValidationError()
