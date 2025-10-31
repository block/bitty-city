package xyz.block.bittycity.outie.utils

import arrow.core.raise.result
import org.bitcoinj.base.Address

object AddressFormatter {
  private val addressLikeRegex = Regex("^(1|3|bc1|bc1p|m|n|2|tb1|tb1p)[a-zA-HJ-NP-Z0-9]{10,87}\$")
  private val captureRegex = Regex("^(.{4})(.{4}).*(.{2})$")
  private const val OUT_PATTERN = "$1 $2...$3"

  fun truncate(address: String): Result<String> = result {
    if (!addressLikeRegex.matches(address)) {
      raise(IllegalArgumentException("Invalid address: $address"))
    }
    address.replace(captureRegex, OUT_PATTERN)
  }

  fun Address.truncate(): String = toString().replace(captureRegex, OUT_PATTERN)
}
