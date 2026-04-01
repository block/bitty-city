# Change Log

## [0.2.2]

* Reduce Rush fee when balance is above minimum but below minimum + fee.
* Do not cache errors after on-chain submission to allow retries to discover real outcome.

## [0.2.1]

* Moved withdrawal eligibility client contract to common EligibilityClient.


## [0.2.0]

* Moved BalanceId, BitcoinAccount and BitcoinAccount to the common module, because they are reusable across withdrawals and deposits.

## [0.1.0]

* Downgrade JVM target version to 11 to improve compatibility.

## [0.0.7]

* Returns InvalidProcessState when an operation is attempted on a failed withdrawal.


