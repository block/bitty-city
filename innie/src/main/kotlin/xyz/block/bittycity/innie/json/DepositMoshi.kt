package xyz.block.bittycity.innie.json

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.innie.models.DepositToken
import java.time.Instant

/**
 * Provides a configured Moshi instance for deposit-related JSON serialization/deserialization.
 */
object DepositMoshi {

  fun create(): Moshi = Moshi.Builder()
    .add(InstantAdapter())
    .add(BitcoinsAdapter())
    .add(CustomerIdAdapter())
    .add(DepositTokenAdapter())
    .add(RequirementIdJsonAdapter)
    .addLast(KotlinJsonAdapterFactory())
    .build()

  class InstantAdapter {
    @ToJson
    fun toJson(instant: Instant): Long = instant.toEpochMilli()

    @FromJson
    fun fromJson(epochMilli: Long): Instant = Instant.ofEpochMilli(epochMilli)
  }

  class BitcoinsAdapter {
    @ToJson
    fun toJson(bitcoins: Bitcoins): Long = bitcoins.units

    @FromJson
    fun fromJson(value: Long): Bitcoins = Bitcoins(value)
  }

  class CustomerIdAdapter {
    @ToJson
    fun toJson(customerId: CustomerId): String = customerId.id

    @FromJson
    fun fromJson(id: String): CustomerId = CustomerId(id)
  }

  class DepositTokenAdapter {
    @ToJson
    fun toJson(token: DepositToken): String = token.toString()

    @FromJson
    fun fromJson(token: String): DepositToken = DepositToken.create(token)
  }

  object RequirementIdJsonAdapter : JsonAdapter<xyz.block.bittycity.innie.models.RequirementId>() {
    @ToJson
    override fun toJson(writer: JsonWriter, value: xyz.block.bittycity.innie.models.RequirementId?) {
      if (value == null) {
        writer.nullValue()
        return
      }
      writer.beginObject()
      writer.name("name").value(value.name)
      writer.name("requiresSecureEndpoint").value(value.requiresSecureEndpoint)
      writer.endObject()
    }

    @FromJson
    override fun fromJson(reader: JsonReader): xyz.block.bittycity.innie.models.RequirementId? {
      var name: String? = null
      reader.beginObject()
      while (reader.hasNext()) {
        when (reader.nextName()) {
          "name" -> name = reader.nextString()
          else -> reader.skipValue()
        }
      }
      reader.endObject()
      return name?.let { xyz.block.bittycity.innie.models.RequirementId.valueOf(it) }
    }
  }
}
