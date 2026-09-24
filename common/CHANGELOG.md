# Change Log

## [0.2.3]

* Added `IdempotencyOperations.deleteErrorResponsesForRequest` and
  `IdempotencyHandler.clearCachedErrors` to remove cached errors for a request id while preserving
  successful responses and in-flight placeholders. Breaking: implementers of
  `IdempotencyOperations` must add the new method.

## [0.2.0]

* Moved BalanceId, BitcoinAccount and BitcoinAccount to this module, because they are reusable across withdrawals and deposits.

## [0.1.0]

* Downgrade JVM target version to 11 to improve compatibility.


