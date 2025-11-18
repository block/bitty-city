package xyz.block.bittycity.common.idempotency

data class SerializableError(
  val message: String?,
  val type: String
) {

  fun asCachedError(): CachedError = CachedError("[$type] $message")

  companion object {
    fun from(t: Throwable): SerializableError = SerializableError(
      message = t.message,
      type = t::class.qualifiedName ?: "Throwable"
    )
  }
}

data class CachedError(override val message: String?) : Throwable(message)
