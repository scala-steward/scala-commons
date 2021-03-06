package com.avsystem.commons
package rest

import com.avsystem.commons.meta._
import com.avsystem.commons.misc.{AbstractValueEnum, AbstractValueEnumCompanion, EnumCtx, ImplicitNotFound}
import com.avsystem.commons.rpc._
import com.avsystem.commons.serialization.GenCodec
import com.avsystem.commons.serialization.GenCodec.ReadFailure
import com.avsystem.commons.serialization.json.{JsonReader, JsonStringInput, JsonStringOutput, RawJson}

import scala.annotation.implicitNotFound
import scala.util.control.NoStackTrace

sealed trait RestValue extends Any {
  def value: String
}

/**
  * Value used as encoding of [[Path]] parameters.
  */
case class PathValue(value: String) extends AnyVal with RestValue
object PathValue {
  def splitDecode(path: String): List[PathValue] =
    path.split("/").iterator.map(s => PathValue(UrlEncoding.decode(s, plusAsSpace = false))).toList match {
      case PathValue("") :: tail => tail
      case res => res
    }

  def encodeJoin(path: List[PathValue]): String =
    path.iterator.map(pv => UrlEncoding.encode(pv.value, spaceAsPlus = false)).mkString("/", "/", "")
}

/**
  * Value used as encoding of [[Header]] parameters.
  */
case class HeaderValue(value: String) extends AnyVal with RestValue

/**
  * Value used as encoding of [[Query]] parameters and [[Body]] parameters of [[FormBody]] methods.
  */
case class QueryValue(value: String) extends AnyVal with RestValue
object QueryValue {
  final val FormKVSep = "="
  final val FormKVPairSep = "&"

  def encode(query: Mapping[QueryValue]): String =
    query.iterator.map { case (name, QueryValue(value)) =>
      s"${UrlEncoding.encode(name, spaceAsPlus = true)}$FormKVSep${UrlEncoding.encode(value, spaceAsPlus = true)}"
    }.mkString(FormKVPairSep)

  def decode(queryString: String): Mapping[QueryValue] = {
    val builder = Mapping.newBuilder[QueryValue]()
    queryString.split(FormKVPairSep).iterator.filter(_.nonEmpty).map(_.split(FormKVSep, 2)).foreach {
      case Array(encname, encvalue) =>
        val name = UrlEncoding.decode(encname, plusAsSpace = true)
        val value = UrlEncoding.decode(encvalue, plusAsSpace = true)
        builder += name -> QueryValue(value)
      case _ => throw new IllegalArgumentException(s"invalid query string $queryString")
    }
    builder.result()
  }
}

/**
  * Value used as encoding of [[Body]] parameters of non-[[FormBody]] methods.
  * Wrapped value MUST be a valid JSON.
  */
case class JsonValue(value: String) extends AnyVal with RestValue
object JsonValue {
  implicit val codec: GenCodec[JsonValue] = GenCodec.create(
    i => JsonValue(i.readCustom(RawJson).getOrElse(i.readSimple().readString())),
    (o, v) => if (!o.writeCustom(RawJson, v.value)) o.writeSimple().writeString(v.value)
  )
}

/**
  * Value used to represent HTTP body. Also used as direct encoding of [[Body]] parameters. Types that have
  * encoding to [[JsonValue]] automatically have encoding to [[HttpBody]] which uses application/json MIME type.
  * There is also a specialized encoding provided for `Unit` which returns empty HTTP body when writing and ignores
  * the body when reading.
  */
sealed trait HttpBody {
  final def contentOpt: Opt[String] = this match {
    case HttpBody(content, _) => Opt(content)
    case HttpBody.Empty => Opt.Empty
  }

  final def forNonEmpty(consumer: (String, String) => Unit): Unit = this match {
    case HttpBody(content, mimeType) => consumer(content, mimeType)
    case HttpBody.Empty =>
  }

  final def readContent(): String = this match {
    case HttpBody(content, _) => content
    case HttpBody.Empty => throw new ReadFailure("Expected non-empty body")
  }

  final def readJson(): JsonValue = JsonValue(readContent(HttpBody.JsonType))
  final def readForm(): String = readContent(HttpBody.FormType)

  final def readContent(mimeType: String): String = this match {
    case HttpBody(content, `mimeType`) => content
    case HttpBody(_, actualMimeType) =>
      throw new ReadFailure(s"Expected body with $mimeType type, got $actualMimeType")
    case HttpBody.Empty =>
      throw new ReadFailure(s"Expected body with $mimeType type, got empty body")
  }

  final def defaultStatus: Int = this match {
    case HttpBody.Empty => 204
    case _ => 200
  }

  final def defaultResponse: RestResponse =
    RestResponse(defaultStatus, Mapping.empty, this)
}
object HttpBody {
  case object Empty extends HttpBody
  final case class NonEmpty(content: String, mimeType: String) extends HttpBody

  def empty: HttpBody = Empty

  def apply(content: String, mimeType: String): HttpBody =
    NonEmpty(content, mimeType)

  def unapply(body: HttpBody): Opt[(String, String)] = body match {
    case Empty => Opt.Empty
    case NonEmpty(content, mimeType) => Opt((content, mimeType))
  }

  final val PlainType = "text/plain"
  final val JsonType = "application/json"
  final val FormType = "application/x-www-form-urlencoded"

  def plain(content: OptArg[String] = OptArg.Empty): HttpBody =
    content.toOpt.map(HttpBody(_, PlainType)).getOrElse(Empty)

  def json(json: JsonValue): HttpBody = HttpBody(json.value, JsonType)

  def createFormBody(values: Mapping[QueryValue]): HttpBody =
    if (values.isEmpty) HttpBody.Empty else HttpBody(QueryValue.encode(values), FormType)

  def parseFormBody(body: HttpBody): Mapping[QueryValue] = body match {
    case HttpBody.Empty => Mapping.empty
    case _ => QueryValue.decode(body.readForm())
  }

  def createJsonBody(fields: Mapping[JsonValue]): HttpBody =
    if (fields.isEmpty) HttpBody.Empty else {
      val sb = new JStringBuilder
      val oo = new JsonStringOutput(sb).writeObject()
      fields.foreach {
        case (key, JsonValue(json)) =>
          oo.writeField(key).writeRawJson(json)
      }
      oo.finish()
      HttpBody.json(JsonValue(sb.toString))
    }

  def parseJsonBody(body: HttpBody): Mapping[JsonValue] = body match {
    case HttpBody.Empty => Mapping.empty
    case _ =>
      val oi = new JsonStringInput(new JsonReader(body.readJson().value)).readObject()
      val builder = Mapping.newBuilder[JsonValue]()
      while (oi.hasNext) {
        val fi = oi.nextField()
        builder += ((fi.fieldName, JsonValue(fi.readRawJson())))
      }
      builder.result()
  }

  implicit val emptyBodyForUnit: AsRawReal[HttpBody, Unit] =
    AsRawReal.create(_ => HttpBody.Empty, _ => ())
  implicit def httpBodyJsonAsRaw[T](implicit jsonAsRaw: AsRaw[JsonValue, T]): AsRaw[HttpBody, T] =
    AsRaw.create(v => HttpBody.json(jsonAsRaw.asRaw(v)))
  implicit def httpBodyJsonAsReal[T](implicit jsonAsReal: AsReal[JsonValue, T]): AsReal[HttpBody, T] =
    AsReal.create(v => jsonAsReal.asReal(v.readJson()))

  @implicitNotFound("Cannot deserialize ${T} from HttpBody, because:\n#{forJson}")
  implicit def asRealNotFound[T](
    implicit forJson: ImplicitNotFound[AsReal[JsonValue, T]]
  ): ImplicitNotFound[AsReal[HttpBody, T]] = ImplicitNotFound()

  @implicitNotFound("Cannot serialize ${T} into HttpBody, because:\n#{forJson}")
  implicit def asRawNotFound[T](
    implicit forJson: ImplicitNotFound[AsRaw[JsonValue, T]]
  ): ImplicitNotFound[AsRaw[HttpBody, T]] = ImplicitNotFound()
}

/**
  * Enum representing HTTP methods.
  */
final class HttpMethod(implicit enumCtx: EnumCtx) extends AbstractValueEnum
object HttpMethod extends AbstractValueEnumCompanion[HttpMethod] {
  final val GET, HEAD, POST, PUT, DELETE, CONNECT, OPTIONS, TRACE, PATCH: Value = new HttpMethod
}

case class RestParameters(
  @multi @tagged[Path] path: List[PathValue] = Nil,
  @multi @tagged[Header] headers: Mapping[HeaderValue] = Mapping.empty,
  @multi @tagged[Query] query: Mapping[QueryValue] = Mapping.empty
) {
  def append(method: RestMethodMetadata[_], otherParameters: RestParameters): RestParameters =
    RestParameters(
      path ::: method.applyPathParams(otherParameters.path),
      headers ++ otherParameters.headers,
      query ++ otherParameters.query
    )
}
object RestParameters {
  final val Empty = RestParameters()
}

case class HttpErrorException(code: Int, payload: OptArg[String] = OptArg.Empty, cause: Throwable = null)
  extends RuntimeException(s"HTTP ERROR $code${payload.fold("")(p => s": $p")}", cause) with NoStackTrace {
  def toResponse: RestResponse = RestResponse.plain(code, payload)
}

case class RestRequest(method: HttpMethod, parameters: RestParameters, body: HttpBody)
case class RestResponse(code: Int, headers: Mapping[HeaderValue], body: HttpBody) {
  def toHttpError: HttpErrorException =
    HttpErrorException(code, body.contentOpt.toOptArg)
  def ensureNonError: RestResponse =
    if (code >= 200 && code < 300) this else throw toHttpError
}

object RestResponse {
  def plain(status: Int, message: OptArg[String] = OptArg.Empty): RestResponse =
    RestResponse(status, Mapping.empty, HttpBody.plain(message))

  class LazyOps(private val resp: () => RestResponse) extends AnyVal {
    def recoverHttpError: RestResponse = try resp() catch {
      case e: HttpErrorException => e.toResponse
    }
  }
  implicit def lazyOps(resp: => RestResponse): LazyOps = new LazyOps(() => resp)

  implicit class AsyncOps(private val asyncResp: RawRest.Async[RestResponse]) extends AnyVal {
    def recoverHttpError: RawRest.Async[RestResponse] =
      callback => asyncResp {
        case Failure(e: HttpErrorException) => callback(Success(e.toResponse))
        case tr => callback(tr)
      }
  }

  implicit def bodyBasedFromResponse[T](implicit bodyAsReal: AsReal[HttpBody, T]): AsReal[RestResponse, T] =
    AsReal.create(resp => bodyAsReal.asReal(resp.ensureNonError.body))

  implicit def bodyBasedToResponse[T](implicit bodyAsRaw: AsRaw[HttpBody, T]): AsRaw[RestResponse, T] =
    AsRaw.create(value => bodyAsRaw.asRaw(value).defaultResponse.recoverHttpError)

  implicit def effectFromAsyncResp[F[_], T](
    implicit fromAsync: RawRest.FromAsync[F], asResponse: AsReal[RestResponse, T]
  ): AsReal[RawRest.Async[RestResponse], Try[F[T]]] =
    AsReal.create(async => Success(fromAsync.fromAsync(RawRest.mapAsync(async)(resp => asResponse.asReal(resp)))))

  implicit def effectToAsyncResp[F[_], T](
    implicit toAsync: RawRest.ToAsync[F], asResponse: AsRaw[RestResponse, T]
  ): AsRaw[RawRest.Async[RestResponse], Try[F[T]]] =
    AsRaw.create(_.fold(
      RawRest.failingAsync,
      ft => RawRest.mapAsync(toAsync.toAsync(ft))(asResponse.asRaw)
    ).recoverHttpError)

  // following two implicits forward implicit-not-found error messages for HttpBody as error messages for RestResponse

  @implicitNotFound("Cannot deserialize ${T} from RestResponse, because:\n#{forBody}")
  implicit def asRealNotFound[T](
    implicit forBody: ImplicitNotFound[AsReal[HttpBody, T]]
  ): ImplicitNotFound[AsReal[RestResponse, T]] = ImplicitNotFound()

  @implicitNotFound("Cannot serialize ${T} into RestResponse, because:\n#{forBody}")
  implicit def asRawNotFound[T](
    implicit forBody: ImplicitNotFound[AsRaw[HttpBody, T]]
  ): ImplicitNotFound[AsRaw[RestResponse, T]] = ImplicitNotFound()

  // following two implicits provide nice error messages when serialization is lacking for HTTP method result
  // while the async wrapper is fine (e.g. Future)

  @implicitNotFound("${F}[${T}] is not a valid result type of HTTP REST method because:\n#{forResponseType}")
  implicit def effAsyncAsRealNotFound[F[_], T](implicit
    fromAsync: RawRest.FromAsync[F],
    forResponseType: ImplicitNotFound[AsReal[RestResponse, T]]
  ): ImplicitNotFound[AsReal[RawRest.Async[RestResponse], Try[F[T]]]] = ImplicitNotFound()

  @implicitNotFound("${F}[${T}] is not a valid result type of HTTP REST method because:\n#{forResponseType}")
  implicit def effAsyncAsRawNotFound[F[_], T](implicit
    toAsync: RawRest.ToAsync[F],
    forResponseType: ImplicitNotFound[AsRaw[RestResponse, T]]
  ): ImplicitNotFound[AsRaw[RawRest.Async[RestResponse], Try[F[T]]]] = ImplicitNotFound()

  // following two implicits provide nice error messages when result type of HTTP method is totally wrong

  @implicitNotFound("#{forResponseType}")
  implicit def asyncAsRealNotFound[T](
    implicit forResponseType: ImplicitNotFound[HttpResponseType[T]]
  ): ImplicitNotFound[AsReal[RawRest.Async[RestResponse], Try[T]]] = ImplicitNotFound()

  @implicitNotFound("#{forResponseType}")
  implicit def asyncAsRawNotFound[T](
    implicit forResponseType: ImplicitNotFound[HttpResponseType[T]]
  ): ImplicitNotFound[AsRaw[RawRest.Async[RestResponse], Try[T]]] = ImplicitNotFound()
}
