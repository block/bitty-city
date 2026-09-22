# Change Log

## [Unreleased]

* Added `IdempotencyOperations.deleteResponsesForRequest` and
  `IdempotencyHandler.clearCachedResponses` to remove every cached response for a request id, so a
  `CachedError` that blocks replays can be cleared. Breaking: implementers of
  `IdempotencyOperations` must add the new method.

## [0.2.0]

* Moved BalanceId, BitcoinAccount and BitcoinAccount to this module, because they are reusable across withdrawals and deposits.

## [0.1.0]

* Downgrade JVM target version to 11 to improve compatibility.


