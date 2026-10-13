package rules.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.booleanOrNull
import rules.model.Value

/**
 * Stores literal values as plain JSON (`"Germany"`, `40`, `true`, `["Munich", "Berlin"]`, `null`)
 * instead of tagged objects, so rule files stay short and readable.
 * Numbers are written and read as exact decimal text, never through `Double`.
 */
object ValueSerializer : KSerializer<Value> {
    override val descriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Value) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("Value supports only JSON")
        json.encodeJsonElement(toJson(value))
    }

    override fun deserialize(decoder: Decoder): Value {
        val json = decoder as? JsonDecoder ?: throw SerializationException("Value supports only JSON")
        return fromJson(json.decodeJsonElement())
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun toJson(value: Value): JsonElement = when (value) {
        is Value.Str -> JsonPrimitive(value.value)
        is Value.Num -> JsonUnquotedLiteral(value.value.toPlainString())
        is Value.Bool -> JsonPrimitive(value.value)
        is Value.ListOf -> JsonArray(value.items.map(::toJson))
        Value.Missing -> JsonNull
    }

    private fun fromJson(element: JsonElement): Value = when (element) {
        JsonNull -> Value.Missing
        is JsonPrimitive -> when {
            element.isString -> Value.Str(element.content)
            element.booleanOrNull != null -> Value.of(element.booleanOrNull!!)
            else -> element.content.toBigDecimalOrNull()?.let(Value::Num)
                ?: throw SerializationException("Invalid number literal: ${element.content}")
        }
        is JsonArray -> Value.ListOf(element.map(::fromJson))
        is JsonObject -> throw SerializationException("Objects are not valid literal values: $element")
    }
}
