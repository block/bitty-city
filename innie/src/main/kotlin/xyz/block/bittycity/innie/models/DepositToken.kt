package xyz.block.bittycity.innie.models

class DepositToken(val token: String) {
  override fun toString(): String = "$PREFIX$token"
  fun outputToken(): String = this.token.removePrefix(PREFIX)

  companion object {
    const val PREFIX = "BTCD_"

    fun create(token: String): DepositToken {
      return DepositToken(PREFIX + token)
    }

    fun isValidTokenFormat(token: String): Boolean =
      token.startsWith(PREFIX) && token.removePrefix(PREFIX).isNotEmpty()
  }
}
