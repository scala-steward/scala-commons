package com.avsystem.commons
package serialization.json

import java.math.MathContext

/**
  * Specifies format used by `JsonStringOutput.writeBinary` / `JsonStringInput.readBinary` to represent byte arrays.
  */
sealed trait JsonBinaryFormat
object JsonBinaryFormat {
  /** Specifies that binary data should be represented as JSON array of numeric, signed byte values. */
  case object ByteArray extends JsonBinaryFormat
  /** Specifies that binary data should be represented as JSON lowercase hex string */
  case object HexString extends JsonBinaryFormat
}

/**
  * Specifies format used by `JsonStringOutput.writeTimestamp` / `JsonStringInput.readTimestamp`
  * to represent timestamps.
  */
sealed trait JsonDateFormat
object JsonDateFormat {
  /**
    * Specifies that a timestamp should be represented in ISO 8601 format with UTC time zone,
    * e.g. `2012-02-13T07:30:21.232Z`
    */
  case object IsoInstant extends JsonDateFormat
  /**
    * Specifies that a timestamp should be represented as JSON number containing number of milliseconds
    * since UNIX epoch.
    */
  case object EpochMillis extends JsonDateFormat
}

/**
  * Adjusts format of JSON produced by [[JsonStringOutput]].
  *
  * @param formatting   JSON formatting options, controls how whitespace is added to JSON output
  * @param asciiOutput  when set, all non-ASCII characters in strings will be unicode-escaped
  * @param mathContext  `MathContext` used when deserializing `BigDecimal`s
  * @param dateFormat   format used to represent timestamps
  * @param binaryFormat format used to represent binary data (byte arrays)
  */
case class JsonOptions(
  formatting: JsonFormatting = JsonFormatting.Compact,
  asciiOutput: Boolean = false,
  mathContext: MathContext = BigDecimal.defaultMathContext,
  dateFormat: JsonDateFormat = JsonDateFormat.IsoInstant,
  binaryFormat: JsonBinaryFormat = JsonBinaryFormat.ByteArray
)
object JsonOptions {
  final val Default = JsonOptions()
  final val Pretty = JsonOptions(formatting = JsonFormatting.Pretty)
}

case class JsonFormatting(
  indentSize: OptArg[Int] = OptArg.Empty,
  afterColon: Int = 0
)
object JsonFormatting {
  final val Compact = JsonFormatting()
  final val Pretty = JsonFormatting(indentSize = 2, afterColon = 1)
}
