package xyz.block.bittycity.innie.models

import app.cash.quiver.extensions.catch
import app.cash.quiver.extensions.mapFailure
import arrow.core.raise.result
import java.util.UUID

class DepositReversalToken(val uuid: UUID) {
  override fun toString(): String = "$PREFIX$uuid"

  companion object {
    const val PREFIX = "BTCDR_"

    fun parse(token: String): Result<DepositReversalToken> = result {
      val uuidString = token.removePrefix(PREFIX)
      val uuid = Result.catch { UUID.fromString(uuidString) }
        .mapFailure { IllegalArgumentException("Invalid UUID:「$token」", it) }
        .bind()
      DepositReversalToken(uuid)
    }

    fun isValidTokenFormat(token: String): Boolean = Result.catch {
      token.startsWith(PREFIX) &&
              UUID.fromString(token.removePrefix(PREFIX)) != null
    }.getOrDefault(false)
  }
}
