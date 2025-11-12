package xyz.block.bittycity.innie.models

enum class RequirementId(val requiresSecureEndpoint: Boolean) {
  TARGET_WALLET_ADDRESS(false),
  SCAM_WARNING(false),
  SCAM_WARNING_CANCELLED(false),
  USER_CONFIRMATION(true),
  SANCTIONS_WITHDRAWAL_REASON(true),
  SANCTIONS_HELD(true),
  OBSERVED_IN_MEMPOOL(true),
  CONFIRMED_ON_CHAIN(true),
  SELF_ATTESTATION(true),
  SUBMITTED_ON_CHAIN(true),
  FAILED_ON_CHAIN(true),
}