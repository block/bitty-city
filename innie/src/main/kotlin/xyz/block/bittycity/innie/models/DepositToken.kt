package xyz.block.bittycity.innie.models

import app.cash.quiver.extensions.catch
import app.cash.quiver.extensions.mapFailure
import arrow.core.raise.result
import java.util.UUID

class DepositToken(val uuid: UUID) {
  override fun toString(): String = "$PREFIX$uuid"

  companion object {
    const val PREFIX = "BTCD_"

    fun parse(token: String): Result<DepositToken> = result {
      val uuidString = token.removePrefix(PREFIX)
      val uuid = Result.catch { UUID.fromString(uuidString) }
        .mapFailure { IllegalArgumentException("Invalid UUID:「$token」", it) }
        .bind()
      DepositToken(uuid)
    }

    fun isValidTokenFormat(token: String): Boolean = Result.catch {
      token.startsWith(PREFIX) &&
              UUID.fromString(token.removePrefix(PREFIX)) != null
    }.getOrDefault(false)
  }
}