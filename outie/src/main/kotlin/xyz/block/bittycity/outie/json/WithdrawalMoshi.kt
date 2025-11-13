package xyz.block.bittycity.outie.json

import app.cash.quiver.extensions.catch
import app.cash.quiver.extensions.mapFailure
import arrow.core.raise.result
import xyz.block.bittycity.outie.models.BalanceId
import xyz.block.bittycity.common.models.Bitcoins
import xyz.block.bittycity.common.models.CustomerId
import xyz.block.bittycity.common.models.FlatFee
import xyz.block.bittycity.common.models.LedgerTransactionId
import xyz.block.bittycity.common.models.MarginFee
import xyz.block.bittycity.outie.models.RequirementId
import xyz.block.bittycity.common.models.ServiceFee
import xyz.block.bittycity.outie.models.WithdrawalHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdle.AmountHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdle.ConfirmationHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdle.NoteHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdle.ScamWarningHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdle.SelfAttestationHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdle.SpeedHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdle.TargetWalletAddressHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdle.WithdrawalReasonHurdle
import xyz.block.bittycity.outie.models.WithdrawalHurdleResponse.AmountHurdleResponse
import xyz.block.bittycity.outie.models.WithdrawalHurdleResponse.ConfirmationHurdleResponse
import xyz.block.bittycity.outie.models.WithdrawalHurdleResponse.NoteHurdleResponse
import xyz.block.bittycity.outie.models.WithdrawalHurdleResponse.ScamWarningHurdleResponse
import xyz.block.bittycity.outie.models.WithdrawalHurdleResponse.SpeedHurdleResponse
import xyz.block.bittycity.outie.models.WithdrawalHurdleResponse.TargetWalletAddressHurdleResponse
import xyz.block.bittycity.outie.models.WithdrawalHurdleResponse.WithdrawalReasonHurdleResponse
import xyz.block.bittycity.outie.models.WithdrawalNotification
import xyz.block.bittycity.outie.models.WithdrawalSpeed
import xyz.block.bittycity.outie.models.WithdrawalState
import xyz.block.bittycity.outie.models.WithdrawalToken
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.bitcoinj.base.AddressParser
import org.bitcoinj.base.Address
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import xyz.block.domainapi.Input
import xyz.block.domainapi.ResultCode
import xyz.block.domainapi.UserInteraction
import java.lang.reflect.Type
import java.time.Instant
import kotlin.time.Duration
import org.bitcoinj.base.BitcoinNetwork

/**
 * Provides a configured Moshi instance for withdrawal-related JSON serialization/deserialization.
 * This centralizes all the custom adapters needed for withdrawal processing.
 */
object WithdrawalMoshi {

  fun create(): Moshi = Moshi.Builder()
    .add(Duration::class.java, DurationAdapter())
    .add(ServiceFeeAdapter())
    .add(InstantAdapter())
    .add(AddressAdapter())
    .add(MoneyAdapter())
    .add(BitcoinsAdapter())
    .add(CustomerIdAdapter())
    .add(WithdrawalTokenAdapter())
    .add(BalanceIdAdapter())
    .add(LedgerTransactionIdAdapter())
    .add(WithdrawalStateAdapter())
    .add(RequirementIdJsonAdapter)
    .add(HurdleResponseAdapter)
    .add(UserInteractionAdapterFactory())
    .addLast(KotlinJsonAdapterFactory())
    .build()

  // Custom Moshi adapters for non-serializable types
  class InstantAdapter {
    @ToJson
    fun toJson(instant: Instant): Long = instant.toEpochMilli()

    @FromJson
    fun fromJson(epochMilli: Long): Instant = Instant.ofEpochMilli(epochMilli)
  }

  class AddressAdapter {
    @ToJson
    fun toJson(address: Address): String = address.toString()

    @FromJson
    fun fromJson(addressString: String): Address = result {
      val mainNet = Result.catch { AddressParser.getDefault(BitcoinNetwork.MAINNET).parseAddress(addressString) }
      val testNet = when {
        mainNet.isSuccess -> null
        else -> Result.catch { AddressParser.getDefault(BitcoinNetwork.TESTNET).parseAddress(addressString) }
      }
      (testNet ?: mainNet).bind()
    }.mapFailure { IllegalArgumentException("Invalid address format: $addressString", it) }
      .getOrThrow()
  }

  class MoneyAdapter {
    @ToJson
    fun toJson(money: Money): Map<String, Any> = mapOf(
      "currency" to money.currencyUnit.code,
      "cents" to money.amountMinorLong
    )

    @FromJson
    fun fromJson(map: Map<String, Any>): Money {
      val currency = map["currency"] as String
      val cents = (map["cents"] as Number).toLong()
      return Money.ofMinor(CurrencyUnit.of(currency), cents)
    }
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

  class WithdrawalTokenAdapter {
    @ToJson
    fun toJson(token: WithdrawalToken): String = token.toString()

    @FromJson
    fun fromJson(token: String): WithdrawalToken = WithdrawalToken.parse(token).getOrThrow()
  }

  class BalanceIdAdapter {
    @ToJson
    fun toJson(balanceId: BalanceId): String = balanceId.id

    @FromJson
    fun fromJson(id: String): BalanceId = BalanceId(id)
  }

  class LedgerTransactionIdAdapter {
    @ToJson
    fun toJson(ledgerTransactionId: LedgerTransactionId): String = ledgerTransactionId.id

    @FromJson
    fun fromJson(id: String): LedgerTransactionId = LedgerTransactionId(id)
  }

  class WithdrawalStateAdapter {
    @ToJson
    fun toJson(state: WithdrawalState): String = state.name

    @FromJson
    fun fromJson(name: String): WithdrawalState = WithdrawalState.byName(name).getOrThrow()
  }

  class DurationAdapter : JsonAdapter<Duration>() {
    override fun fromJson(reader: JsonReader): Duration? =
      if (reader.peek() == JsonReader.Token.NULL) {
        reader.nextNull()
      } else {
        Duration.parse(reader.nextString())
      }

    override fun toJson(writer: JsonWriter, value: Duration?) {
      if (value == null) {
        writer.nullValue()
      } else {
        writer.value(value.toString())
      }
    }
  }

  class ServiceFeeAdapter {
    @ToJson
    fun toJson(serviceFee: ServiceFee): Map<String, Any> = when (serviceFee) {
      is FlatFee -> mapOf(
        "type" to "FlatFee",
        "value" to serviceFee.value.units
      )
      is MarginFee -> mapOf(
        "type" to "MarginFee",
        "margin" to serviceFee.margin,
        "value" to serviceFee.value.units
      )
    }

    @FromJson
    fun fromJson(json: Map<String, Any>): ServiceFee {
      val type = json["type"] as String
      val value = Bitcoins((json["value"] as Number).toLong())

      return when (type) {
        "FlatFee" -> FlatFee(value)
        "MarginFee" -> {
          val margin = (json["margin"] as Number).toInt()
          MarginFee(margin, value)
        }
        else -> throw IllegalArgumentException("Unknown ServiceFee type: $type")
      }
    }
  }

  object RequirementIdJsonAdapter : JsonAdapter<RequirementId>() {
    @ToJson
    override fun toJson(writer: JsonWriter, value: RequirementId?) {
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
    override fun fromJson(reader: JsonReader): RequirementId? {
      var name: String? = null
      reader.beginObject()
      while (reader.hasNext()) {
        when (reader.nextName()) {
          "name" -> name = reader.nextString()
          else -> reader.skipValue()
        }
      }
      reader.endObject()
      return name?.let { RequirementId.valueOf(it) }
    }
  }

  class UserInteractionAdapterFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? =
      when (Types.getRawType(type)) {
        UserInteraction::class.java -> {
          val hurdleAdapter = moshi.adapter(WithdrawalHurdle::class.java)
          val notificationAdapter = moshi.adapter(WithdrawalNotification::class.java)
          UserInteractionAdapter(hurdleAdapter, notificationAdapter)
        }
        WithdrawalHurdle::class.java -> WithdrawalHurdleAdapter(moshi)
        WithdrawalNotification::class.java -> WithdrawalNotificationAdapter(moshi)
        else -> null
      }
  }

  class WithdrawalNotificationAdapter(val moshi: Moshi) : JsonAdapter<WithdrawalNotification>() {
    override fun fromJson(reader: JsonReader): WithdrawalNotification? {
      val fields = extractStringFields(reader.readJsonValue(), "type", "value")
      val typeString = fields["type"]
      val valueString = fields["value"]

      return when (typeString) {
        "SubmittedOnChainNotification" -> moshi.adapter(
          WithdrawalNotification.SubmittedOnChainNotification::class.java
        ).fromJson(valueString!!)
        "WithdrawalCancelledNotification" -> WithdrawalNotification.WithdrawalCancelledNotification
        "WithdrawalSanctionsHeld" -> WithdrawalNotification.WithdrawalSanctionsHeld
        null -> null
        else -> throw IllegalArgumentException("Unknown withdrawal notification type: $typeString")
      }
    }

    override fun toJson(writer: JsonWriter, value: WithdrawalNotification?) {
      if (value != null) {
        writer.beginObject()
        when (value) {
          is WithdrawalNotification.SubmittedOnChainNotification -> {
            writer.name("type").value("SubmittedOnChainNotification")
            writer.name(
              "value"
            ).value(
              moshi.adapter(
                WithdrawalNotification.SubmittedOnChainNotification::class.java
              ).toJson(value)
            )
          }
          is WithdrawalNotification.WithdrawalCancelledNotification -> {
            writer.name("type").value("WithdrawalCancelledNotification")
          }
          is WithdrawalNotification.WithdrawalSanctionsHeld -> {
            writer.name("type").value("WithdrawalSanctionsHeld")
          }
        }
        writer.endObject()
      } else {
        writer.nullValue()
      }
    }
  }

  class WithdrawalHurdleAdapter(val moshi: Moshi) : JsonAdapter<WithdrawalHurdle>() {
    override fun fromJson(reader: JsonReader): WithdrawalHurdle? {
      val fields = extractStringFields(reader.readJsonValue(), "type", "value")
      val typeString = fields["type"]
      val valueString = fields["value"]

      return when (typeString) {
        "AmountHurdle" -> moshi.adapter(
          AmountHurdle::class.java
        ).fromJson(valueString!!)
        "ConfirmationHurdle" -> moshi.adapter(
          ConfirmationHurdle::class.java
        ).fromJson(valueString!!)
        "NoteHurdle" -> moshi.adapter(
          NoteHurdle::class.java
        ).fromJson(valueString!!)
        "ScamWarningHurdle" -> ScamWarningHurdle
        "SelfAttestationHurdle" -> SelfAttestationHurdle
        "SpeedHurdle" -> moshi.adapter(
          SpeedHurdle::class.java
        ).fromJson(valueString!!)
        "WithdrawalReasonHurdle" -> moshi.adapter(
          WithdrawalReasonHurdle::class.java
        ).fromJson(valueString!!)
        "TargetWalletAddressHurdle" -> moshi.adapter(
          TargetWalletAddressHurdle::class.java
        ).fromJson(valueString!!)
        null -> null
        else -> throw IllegalArgumentException("Unknown withdrawal hurdle type: $typeString")
      }
    }

    override fun toJson(writer: JsonWriter, value: WithdrawalHurdle?) {
      if (value != null) {
        writer.beginObject()
        when (value) {
          is AmountHurdle -> {
            writer.name("type").value("AmountHurdle")
            writer.name(
              "value"
            ).value(moshi.adapter(AmountHurdle::class.java).toJson(value))
          }
          is ConfirmationHurdle -> {
            writer.name("type").value("ConfirmationHurdle")
            writer.name(
              "value"
            ).value(moshi.adapter(ConfirmationHurdle::class.java).toJson(value))
          }
          is NoteHurdle -> {
            writer.name("type").value("NoteHurdle")
            writer.name(
              "value"
            ).value(moshi.adapter(NoteHurdle::class.java).toJson(value))
          }
          is ScamWarningHurdle -> {
            writer.name("type").value("ScamWarningHurdle")
          }
          is SelfAttestationHurdle -> {
            writer.name("type").value("SelfAttestationHurdle")
          }
          is SpeedHurdle -> {
            writer.name("type").value("SpeedHurdle")
            writer.name(
              "value"
            ).value(moshi.adapter(SpeedHurdle::class.java).toJson(value))
          }
          is WithdrawalReasonHurdle -> {
            writer.name("type").value("WithdrawalReasonHurdle")
            writer.name(
              "value"
            ).value(moshi.adapter(WithdrawalReasonHurdle::class.java).toJson(value))
          }
          is TargetWalletAddressHurdle -> {
            writer.name("type").value("TargetWalletAddressHurdle")
            writer.name(
              "value"
            ).value(moshi.adapter(TargetWalletAddressHurdle::class.java).toJson(value))
          }
        }
        writer.endObject()
      } else {
        writer.nullValue()
      }
    }
  }

  class UserInteractionAdapter(
    val hurdleAdapter: JsonAdapter<WithdrawalHurdle>,
    val notificationAdapter: JsonAdapter<WithdrawalNotification>
  ) : JsonAdapter<UserInteraction<RequirementId>>() {
    @FromJson
    override fun fromJson(reader: JsonReader): UserInteraction<RequirementId>? {
      val fields = extractStringFields(reader.readJsonValue(), "type", "value")
      val typeString = fields["type"]
      val valueString = fields["value"]

      return when (typeString) {
        "WithdrawalHurdle" -> hurdleAdapter.fromJson(valueString ?: "")
        "WithdrawalNotification" -> notificationAdapter.fromJson(valueString ?: "")
        else -> throw IllegalArgumentException("Unknown UserInteraction type: $typeString")
      }
    }

    @ToJson
    override fun toJson(writer: JsonWriter, value: UserInteraction<RequirementId>?) {
      if (value != null) {
        writer.beginObject()
        when (value) {
          is WithdrawalHurdle -> {
            writer.name("type").value("WithdrawalHurdle")
            writer.name("value").value(hurdleAdapter.toJson(value))
          }
          is WithdrawalNotification -> {
            writer.name("type").value("WithdrawalNotification")
            writer.name("value").value(notificationAdapter.toJson(value))
          }
          else -> throw IllegalArgumentException("Unknown UserInteraction type: $value")
        }
        writer.endObject()
      } else {
        writer.nullValue()
      }
    }
  }

  object HurdleResponseAdapter : JsonAdapter<Input.HurdleResponse<RequirementId>>() {
    @FromJson
    override fun fromJson(reader: JsonReader): Input.HurdleResponse<RequirementId>? {
      val raw = reader.readJsonValue()
      val stringFields =
        extractStringFields(raw, "id", "code", "note", "selectedSpeed", "reason", "walletAddress")
      val numberFields = extractNumberFields(raw, "userAmount")
      val idString = stringFields["id"]
      val codeString = stringFields["code"]

      return when (idString) {
        "TARGET_WALLET_ADDRESS" -> {
          val walletAddress = stringFields["walletAddress"]
          TargetWalletAddressHurdleResponse(ResultCode.valueOf(codeString!!), walletAddress)
        }
        "AMOUNT" -> {
          val amountNumber = numberFields["userAmount"]
          AmountHurdleResponse(
            ResultCode.valueOf(codeString!!),
            amountNumber?.let {
              Bitcoins(it.toLong())
            }
          )
        }
        "NOTE" -> {
          val note = stringFields["note"]
          NoteHurdleResponse(ResultCode.valueOf(codeString!!), note)
        }
        "SPEED" -> {
          val selectedSpeedName = stringFields["selectedSpeed"]
          SpeedHurdleResponse(
            ResultCode.valueOf(codeString!!),
            selectedSpeedName?.let {
              WithdrawalSpeed.valueOf(it)
            }
          )
        }
        "SANCTIONS_WITHDRAWAL_REASON" -> {
          val reason = stringFields["reason"]
          WithdrawalReasonHurdleResponse(ResultCode.valueOf(codeString!!), reason)
        }
        "SCAM_WARNING" -> {
          ScamWarningHurdleResponse(ResultCode.valueOf(codeString!!))
        }
        "USER_CONFIRMATION" -> {
          ConfirmationHurdleResponse(ResultCode.valueOf(codeString!!))
        }
        null -> null
        else -> {
          throw IllegalArgumentException("Unknown hurdle response: $idString")
        }
      }
    }

    @ToJson
    override fun toJson(writer: JsonWriter, value: Input.HurdleResponse<RequirementId>?) {
      if (value != null) {
        writer.beginObject()
        writer.name("id").value(value.id.name)
        writer.name("code").value(value.result.name)

        when (value) {
          is TargetWalletAddressHurdleResponse -> {
            writer.name("walletAddress").value(value.walletAddress)
          }
          is AmountHurdleResponse -> {
            writer.name("userAmount").value(value.userAmount?.units)
          }
          is NoteHurdleResponse -> {
            writer.name("note").value(value.note)
          }
          is SpeedHurdleResponse -> {
            writer.name("selectedSpeed").value(value.selectedSpeed?.name)
          }
          is WithdrawalReasonHurdleResponse -> {
            writer.name("reason").value(value.reason)
          }
          is ScamWarningHurdleResponse, is ConfirmationHurdleResponse -> { }
        }
        writer.endObject()
      } else {
        writer.nullValue()
      }
    }
  }

  private fun extractStringFields(raw: Any?, vararg keys: String): Map<String, String?> {
    if (raw !is Map<*, *>) return emptyMap()

    val result = mutableMapOf<String, String?>()
    for (key in keys) {
      val value = (raw[key] as? String)
      result[key] = value
    }
    return result
  }

  private fun extractNumberFields(raw: Any?, vararg keys: String): Map<String, Number?> {
    if (raw !is Map<*, *>) return emptyMap()

    val result = mutableMapOf<String, Number?>()
    for (key in keys) {
      val value = (raw[key] as? Number)
      result[key] = value
    }
    return result
  }
}
