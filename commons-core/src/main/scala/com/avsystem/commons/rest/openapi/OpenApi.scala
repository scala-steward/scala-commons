package com.avsystem.commons
package rest.openapi

import com.avsystem.commons.misc.{AbstractValueEnum, AbstractValueEnumCompanion, EnumCtx}
import com.avsystem.commons.serialization.{transientDefault => td, _}

case class OpenApi(
  openapi: String = OpenApi.Version,
  info: Info,
  paths: Paths,
  @td servers: List[Server] = Nil,
  @td components: OptArg[Components] = OptArg.Empty,
  @td security: List[SecurityRequirement] = Nil,
  @td tags: List[Tag] = Nil,
  @td externalDocs: OptArg[ExternalDocumentation] = OptArg.Empty
)
object OpenApi extends HasGenCodec[OpenApi] {
  final val Version = "3.0.1"
}

case class Info(
  title: String,
  version: String,
  @td license: OptArg[License] = OptArg.Empty,
  @td description: OptArg[String] = OptArg.Empty,
  @td termsOfService: OptArg[String] = OptArg.Empty,
  @td contact: OptArg[Contact] = OptArg.Empty
)
object Info extends HasGenCodec[Info]

case class Contact(
  @td name: OptArg[String] = OptArg.Empty,
  @td url: OptArg[String] = OptArg.Empty,
  @td email: OptArg[String] = OptArg.Empty
)
object Contact extends HasGenCodec[Contact]

case class License(
  name: String,
  @td url: OptArg[String] = OptArg.Empty
)
object License extends HasGenCodec[License]

case class Server(
  url: String,
  @td description: OptArg[String] = OptArg.Empty,
  @td serverVariables: Map[String, ServerVariable] = Map.empty
)
object Server extends HasGenCodec[Server]

case class ServerVariable(
  default: String,
  @td enum: List[String] = Nil,
  @td description: OptArg[String] = OptArg.Empty
)
object ServerVariable extends HasGenCodec[ServerVariable]

@transparent case class Paths(paths: Map[String, RefOr[PathItem]])
object Paths extends HasGenCodec[Paths]

case class PathItem(
  @td summary: OptArg[String] = OptArg.Empty,
  @td description: OptArg[String] = OptArg.Empty,
  @td get: OptArg[Operation] = OptArg.Empty,
  @td put: OptArg[Operation] = OptArg.Empty,
  @td post: OptArg[Operation] = OptArg.Empty,
  @td delete: OptArg[Operation] = OptArg.Empty,
  @td options: OptArg[Operation] = OptArg.Empty,
  @td head: OptArg[Operation] = OptArg.Empty,
  @td patch: OptArg[Operation] = OptArg.Empty,
  @td trace: OptArg[Operation] = OptArg.Empty,
  @td servers: List[Server] = Nil,
  @td parameters: List[RefOr[Parameter]] = Nil
)
object PathItem extends HasGenCodec[PathItem]

case class Operation(
  responses: Responses,
  @td tags: List[String] = Nil,
  @td summary: OptArg[String] = OptArg.Empty,
  @td description: OptArg[String] = OptArg.Empty,
  @td externalDocs: OptArg[ExternalDocumentation] = OptArg.Empty,
  @td operationId: OptArg[String] = OptArg.Empty,
  @td parameters: List[RefOr[Parameter]] = Nil,
  @td requestBody: OptArg[RefOr[RequestBody]] = OptArg.Empty,
  @td callbacks: Map[String, RefOr[Callback]] = Map.empty,
  @td deprecated: Boolean = false,
  @td security: List[SecurityRequirement] = Nil,
  @td servers: List[Server] = Nil
)
object Operation extends HasGenCodec[Operation]

case class Responses(
  byStatusCode: Map[Int, RefOr[Response]] = Map.empty,
  default: OptArg[RefOr[Response]] = OptArg.Empty
)
object Responses {
  final val DefaultField = "default"

  implicit val codec: GenCodec[Responses] = GenCodec.createNullableObject(
    oi => {
      var default = OptArg.empty[RefOr[Response]]
      val byStatusCode = Map.newBuilder[Int, RefOr[Response]]
      while (oi.hasNext) {
        val fi = oi.nextField()
        fi.fieldName match {
          case DefaultField =>
            default = GenCodec.read[RefOr[Response]](fi)
          case status =>
            byStatusCode += ((status.toInt, GenCodec.read[RefOr[Response]](fi)))
        }
      }
      Responses(byStatusCode.result(), default)
    },
    (oo, v) => {
      v.default.foreach(resp => GenCodec.write[RefOr[Response]](oo.writeField(DefaultField), resp))
      v.byStatusCode.foreach {
        case (status, resp) =>
          GenCodec.write[RefOr[Response]](oo.writeField(status.toString), resp)
      }
    }
  )
}

case class Components(
  @td schemas: Map[String, RefOr[Schema]] = Map.empty,
  @td responses: Map[String, RefOr[Response]] = Map.empty,
  @td parameters: Map[String, RefOr[Parameter]] = Map.empty,
  @td examples: Map[String, RefOr[Example]] = Map.empty,
  @td requestBodies: Map[String, RefOr[RequestBody]] = Map.empty,
  @td headers: Map[String, RefOr[Header]] = Map.empty,
  @td securitySchemes: Map[String, RefOr[SecurityScheme]] = Map.empty,
  @td links: Map[String, RefOr[Link]] = Map.empty,
  @td callbacks: Map[String, RefOr[Callback]] = Map.empty
)
object Components extends HasGenCodec[Components]

@transparent case class SecurityRequirement(schemes: Map[String, List[String]])
object SecurityRequirement extends HasGenCodec[SecurityRequirement]

case class Tag(
  name: String,
  @td description: OptArg[String] = OptArg.Empty,
  @td externalDocs: OptArg[ExternalDocumentation] = OptArg.Empty
)
object Tag extends HasGenCodec[Tag]

case class ExternalDocumentation(
  url: String,
  @td description: OptArg[String] = OptArg.Empty
)
object ExternalDocumentation extends HasGenCodec[ExternalDocumentation]

case class Schema(
  @td `type`: OptArg[DataType] = OptArg.Empty,
  @td format: OptArg[String] = OptArg.Empty,
  @td title: OptArg[String] = OptArg.Empty,
  @td description: OptArg[String] = OptArg.Empty,
  @td nullable: Boolean = false,
  @td readOnly: Boolean = false,
  @td writeOnly: Boolean = false,
  @td xml: OptArg[Xml] = OptArg.Empty,
  @td externalDocs: OptArg[ExternalDocumentation] = OptArg.Empty,
  @td deprecated: Boolean = false,

  @td multipleOf: OptArg[BigDecimal] = OptArg.Empty,
  @td maximum: OptArg[BigDecimal] = OptArg.Empty,
  @td exclusiveMaximum: Boolean = false,
  @td minimum: OptArg[BigDecimal] = OptArg.Empty,
  @td exclusiveMinimum: Boolean = false,

  @td maxLength: OptArg[Int] = OptArg.Empty,
  @td minLength: OptArg[Int] = OptArg.Empty,
  @td pattern: OptArg[String] = OptArg.Empty,

  @td items: OptArg[RefOr[Schema]] = OptArg.Empty,
  @td maxItems: OptArg[Int] = OptArg.Empty,
  @td minItems: OptArg[Int] = OptArg.Empty,
  @td uniqueItems: Boolean = false,

  @td properties: Map[String, RefOr[Schema]] = Map.empty,
  @td additionalProperties: OptArg[RefOr[Schema]] = OptArg.Empty, //TODO: boolean value support
  @td maxProperties: OptArg[Int] = OptArg.Empty,
  @td minProperties: OptArg[Int] = OptArg.Empty,
  @td required: List[String] = Nil,

  @td allOf: List[RefOr[Schema]] = Nil,
  @td oneOf: List[RefOr[Schema]] = Nil,
  @td anyOf: List[RefOr[Schema]] = Nil,
  @td not: OptArg[RefOr[Schema]] = OptArg.Empty,
  @td discriminator: OptArg[Discriminator] = OptArg.Empty,

  @td enum: List[String] = Nil //TODO: other values than strings

  //TODO: default
)
object Schema extends HasGenCodec[Schema] {
  final val Boolean = Schema(`type` = DataType.Boolean)
  final val Char = Schema(`type` = DataType.String, minLength = 1, maxLength = 1)
  final val Byte = Schema(`type` = DataType.Integer, format = Format.Int32,
    minimum = BigDecimal(scala.Byte.MinValue), maximum = BigDecimal(scala.Byte.MaxValue))
  final val Short = Schema(`type` = DataType.Integer, format = Format.Int32,
    minimum = BigDecimal(scala.Short.MinValue), maximum = BigDecimal(scala.Short.MaxValue))
  final val Int = Schema(`type` = DataType.Integer, format = Format.Int32)
  final val Long = Schema(`type` = DataType.Integer, format = Format.Int64)
  final val Float = Schema(`type` = DataType.Number, format = Format.Float)
  final val Double = Schema(`type` = DataType.Number, format = Format.Double)
  final val Integer = Schema(`type` = DataType.Integer)
  final val Number = Schema(`type` = DataType.Number)
  final val String = Schema(`type` = DataType.String)
  final val Date = Schema(`type` = DataType.String, format = Format.Date)
  final val DateTime = Schema(`type` = DataType.String, format = Format.DateTime)
  final val Uuid = Schema(`type` = DataType.String, format = Format.Uuid)
  final val Password = Schema(`type` = DataType.String, format = Format.Password)
  final val Binary = Schema(`type` = DataType.String, format = Format.Binary)
  final val Email = Schema(`type` = DataType.String, format = Format.Email)

  def arrayOf(items: RefOr[Schema], uniqueItems: Boolean = false): Schema =
    Schema(`type` = DataType.Array, items = items, uniqueItems = uniqueItems)

  def mapOf(properties: RefOr[Schema]): Schema =
    Schema(`type` = DataType.Object, additionalProperties = properties)

  def enumOf(values: List[String]): Schema =
    Schema(`type` = DataType.String, enum = values)

  def nullable(schema: RefOr[Schema]): Schema = schema match {
    case RefOr.Value(v) => v.copy(nullable = true)
    case ref => Schema(nullable = true, allOf = List(ref))
  }
}

object Format {
  final val Int32 = "int32"
  final val Int64 = "int64"
  final val Float = "float"
  final val Double = "double"
  final val Byte = "byte"
  final val Binary = "binary"
  final val Date = "date"
  final val DateTime = "date-time"
  final val Password = "password"
  final val Email = "email"
  final val Uuid = "uuid"
}

final class DataType(implicit enumCtx: EnumCtx) extends AbstractValueEnum {
  override val name: String = enumCtx.valName.uncapitalize
}
object DataType extends AbstractValueEnumCompanion[DataType] {
  final val String, Number, Integer, Boolean, Array, Object: Value = new DataType
}

case class Discriminator(
  propertyName: String,
  @td mapping: Map[String, String] = Map.empty
)
object Discriminator extends HasGenCodec[Discriminator]

case class Xml(
  @td name: OptArg[String] = OptArg.Empty,
  @td namespace: OptArg[String] = OptArg.Empty,
  @td prefix: OptArg[String] = OptArg.Empty,
  @td attribute: Boolean = false,
  @td wrapped: Boolean = false
)
object Xml extends HasGenCodec[Xml]

case class Response(
  @td description: OptArg[String] = OptArg.Empty,
  @td headers: Map[String, RefOr[Header]] = Map.empty,
  @td content: Map[String, MediaType] = Map.empty,
  @td links: Map[String, RefOr[Link]] = Map.empty
)
object Response extends HasGenCodec[Response]

case class Parameter(
  name: String,
  in: Location,
  @td description: OptArg[String] = OptArg.Empty,
  @td required: Boolean = false,
  @td deprecated: Boolean = false,
  @td allowEmptyValue: Boolean = false,
  @td style: OptArg[Style] = OptArg.Empty,
  @td explode: OptArg[Boolean] = OptArg.Empty,
  @td allowReserved: Boolean = false,
  @td schema: OptArg[RefOr[Schema]] = OptArg.Empty,
  @td content: OptArg[Entry[String, MediaType]] = OptArg.Empty
  //TODO example/examples
)
object Parameter extends HasGenCodec[Parameter]

case class Entry[K, V](key: K, value: V)
object Entry {
  implicit def codec[K: GenKeyCodec, V: GenCodec]: GenCodec[Entry[K, V]] =
    GenCodec.createNullableObject(
      oi => {
        val fi = oi.nextField()
        Entry(GenKeyCodec.read[K](fi.fieldName), GenCodec.read[V](fi))
      },
      (oo, entry) =>
        GenCodec.write[V](oo.writeField(GenKeyCodec.write[K](entry.key)), entry.value)
    )
}

final class Location(implicit enumCtx: EnumCtx) extends AbstractValueEnum {
  override val name: String = enumCtx.valName.uncapitalize
}
object Location extends AbstractValueEnumCompanion[Location] {
  final val Query, Header, Path, Cookie: Value = new Location
}

final class Style(implicit enumCtx: EnumCtx) extends AbstractValueEnum {
  override val name: String = enumCtx.valName.uncapitalize
}
object Style extends AbstractValueEnumCompanion[Style] {
  final val Matrix, Label, Form, Simple, SpaceDelimited, PipeDelimited, DeepObject: Value = new Style
}

case class MediaType(
  @td schema: OptArg[RefOr[Schema]] = OptArg.Empty,
  @td encoding: Map[String, Encoding] = Map.empty
  //TODO: example/examples
)
object MediaType extends HasGenCodec[MediaType]

case class Encoding(
  @td contentType: OptArg[String] = OptArg.Empty,
  @td headers: Map[String, RefOr[Header]] = Map.empty,
  @td style: OptArg[Style] = OptArg.Empty,
  @td explode: OptArg[Boolean] = OptArg.Empty,
  @td allowReserved: Boolean = false,
)
object Encoding extends HasGenCodec[Encoding]

case class Example()
object Example extends HasGenCodec[Example]

case class RequestBody(
  content: Map[String, MediaType],
  @td description: OptArg[String] = OptArg.Empty,
  @td required: Boolean = false
)
object RequestBody extends HasGenCodec[RequestBody]

case class Header(
  @td description: OptArg[String] = OptArg.Empty,
  @td required: Boolean = false,
  @td deprecated: Boolean = false,
  @td allowEmptyValue: Boolean = false,
  @td style: OptArg[Style] = OptArg.Empty,
  @td explode: OptArg[Boolean] = OptArg.Empty,
  @td allowReserved: Boolean = false,
  @td schema: OptArg[RefOr[Schema]] = OptArg.Empty,
  @td content: OptArg[Entry[String, MediaType]] = OptArg.Empty
  //TODO example/examples
)
object Header extends HasGenCodec[Header]

@flatten("type") sealed trait SecurityScheme {
  def description: OptArg[String]
}
object SecurityScheme extends HasGenCodec[SecurityScheme] {
  @name("apiKey") case class ApiKey(
    name: String,
    in: Location,
    @td description: OptArg[String] = OptArg.Empty
  ) extends SecurityScheme

  @name("http") case class Http(
    scheme: String,
    @td bearerFormat: OptArg[String] = OptArg.Empty,
    @td description: OptArg[String] = OptArg.Empty
  ) extends SecurityScheme

  @name("oauth2") case class OAuth2(
    flows: OAuthFlows,
    @td description: OptArg[String] = OptArg.Empty
  ) extends SecurityScheme

  @name("openIdConnect") case class OpenIdConnect(
    openIdConnectUrl: String,
    @td description: OptArg[String] = OptArg.Empty
  ) extends SecurityScheme
}

case class OAuthFlows(
  @td `implicit`: OptArg[OAuthFlow] = OptArg.Empty,
  @td password: OptArg[OAuthFlow] = OptArg.Empty,
  @td clientCredentials: OptArg[OAuthFlow] = OptArg.Empty,
  @td authorizationCode: OptArg[OAuthFlow] = OptArg.Empty
)
object OAuthFlows extends HasGenCodec[OAuthFlows]

case class OAuthFlow(
  scopes: Map[String, String],
  @td authorizationUrl: OptArg[String] = OptArg.Empty,
  @td tokenUrl: OptArg[String] = OptArg.Empty,
  @td refreshUrl: OptArg[String] = OptArg.Empty
)
object OAuthFlow extends HasGenCodec[OAuthFlow]

case class Link(
  @td operationRef: OptArg[String] = OptArg.Empty,
  @td operationId: OptArg[String] = OptArg.Empty,
  @td description: OptArg[String] = OptArg.Empty,
  @td server: OptArg[Server] = OptArg.Empty
  //TODO parameters, requestBody
)
object Link extends HasGenCodec[Link]

@transparent case class Callback(byExpression: Map[String, PathItem])
object Callback extends HasGenCodec[Callback]

sealed trait RefOr[+A]
object RefOr {
  case class Ref(ref: String) extends RefOr[Nothing]
  case class Value[+A](value: A) extends RefOr[A]

  final val RefField = "$ref"

  def apply[A](value: A): RefOr[A] = Value(value)
  def ref[A](ref: String): RefOr[A] = Ref(ref)

  implicit def codec[A: GenCodec]: GenCodec[RefOr[A]] =
    GenCodec.createNullableObject(
      oi => {
        val poi = new PeekingObjectInput(oi)
        val refFieldInput = poi.peekField(RefField).orElse {
          if (poi.peekNextFieldName.contains(RefField)) poi.nextField().opt
          else Opt.Empty
        }
        val res = refFieldInput.map(fi => Ref(fi.readString()))
          .getOrElse(Value(GenCodec.read[A](new ObjectInputAsInput(poi))))
        poi.skipRemaining()
        res
      },
      (oo, value) => value match {
        case Ref(refstr) => oo.writeField(RefField).writeString(refstr)
        case Value(v) => GenCodec.write[A](new ObjectOutputAsOutput(oo, forwardFinish = false), v)
      }
    )
}
