package com.avsystem.commons
package rest

import com.avsystem.commons.misc.{AbstractValueEnum, AbstractValueEnumCompanion, EnumCtx}
import com.avsystem.commons.rpc._
import com.avsystem.commons.serialization.GenCodec.ReadFailure
import com.avsystem.commons.serialization.json.{JsonReader, JsonStringInput, JsonStringOutput}

import scala.util.control.NoStackTrace

sealed trait RestValue extends Any {
  def value: String
}

/**
  * Value used as encoding of [[Path]] parameters.
  */
case class PathValue(value: String) extends AnyVal with RestValue
object PathValue {
  def split(path: String): List[PathValue] =
    path.split("/").iterator.filter(_.nonEmpty).map(PathValue(_)).toList
}

/**
  * Value used as encoding of [[Header]] parameters.
  */
case class HeaderValue(value: String) extends AnyVal with RestValue

/**
  * Value used as encoding of [[Query]] parameters.
  */
case class QueryValue(value: String) extends AnyVal with RestValue

/**
  * Value used as encoding of [[JsonBodyParam]] parameters.
  */
case class JsonValue(value: String) extends AnyVal with RestValue

/**
  * Value used to represent HTTP body. Also used as direct encoding of [[Body]] parameters. Types that have
  * encoding to [[JsonValue]] automatically have encoding to [[HttpBody]] which uses application/json MIME type.
  * There is also a specialized encoding provided for `Unit` which returns empty HTTP body when writing and ignores
  * the body when reading.
  */
sealed trait HttpBody {
  def contentOpt: Opt[String] = this match {
    case HttpBody(content, _) => Opt(content)
    case HttpBody.Empty => Opt.Empty
  }

  def forNonEmpty(consumer: (String, String) => Unit): Unit = this match {
    case HttpBody(content, mimeType) => consumer(content, mimeType)
    case HttpBody.Empty =>
  }

  def readContent(): String = this match {
    case HttpBody(content, _) => content
    case HttpBody.Empty => throw new ReadFailure("Expected non-empty body")
  }

  def readJson(): JsonValue = this match {
    case HttpBody(content, HttpBody.JsonType) => JsonValue(content)
    case HttpBody(_, mimeType) =>
      throw new ReadFailure(s"Expected body with application/json type, got $mimeType")
    case HttpBody.Empty =>
      throw new ReadFailure("Expected body with application/json type, got empty body")
  }
}
object HttpBody {
  object Empty extends HttpBody
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

  def plain(value: String): HttpBody = HttpBody(value, PlainType)
  def json(json: JsonValue): HttpBody = HttpBody(json.value, JsonType)

  def createJsonBody(fields: NamedParams[JsonValue]): HttpBody =
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

  def parseJsonBody(body: HttpBody): NamedParams[JsonValue] = body match {
    case HttpBody.Empty => NamedParams.empty
    case _ =>
      val oi = new JsonStringInput(new JsonReader(body.readJson().value)).readObject()
      val builder = NamedParams.newBuilder[JsonValue]
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
}

/**
  * Enum representing HTTP methods.
  */
final class HttpMethod(implicit enumCtx: EnumCtx) extends AbstractValueEnum
object HttpMethod extends AbstractValueEnumCompanion[HttpMethod] {
  final val GET, PUT, POST, PATCH, DELETE: Value = new HttpMethod
}

case class RestParameters(
  @multi @tagged[Path] path: List[PathValue],
  @multi @tagged[Header] headers: NamedParams[HeaderValue],
  @multi @tagged[Query] query: NamedParams[QueryValue]
) {
  def append(method: RestMethodMetadata[_], otherParameters: RestParameters): RestParameters =
    RestParameters(
      path ::: method.applyPathParams(otherParameters.path),
      headers ++ otherParameters.headers,
      query ++ otherParameters.query
    )
}
object RestParameters {
  final val Empty = RestParameters(Nil, NamedParams.empty, NamedParams.empty)
}

case class HttpErrorException(code: Int, payload: OptArg[String] = OptArg.Empty)
  extends RuntimeException(s"HTTP ERROR $code${payload.fold("")(p => s": $p")}") with NoStackTrace {
  def toResponse: RestResponse =
    RestResponse(code, payload.fold(HttpBody.empty)(HttpBody.plain))
}

case class RestRequest(method: HttpMethod, parameters: RestParameters, body: HttpBody)
case class RestResponse(code: Int, body: HttpBody) {
  def toHttpError: HttpErrorException =
    HttpErrorException(code, body.contentOpt.toOptArg)
  def ensure200OK: RestResponse =
    if (code == 200) this else throw toHttpError
}

object RestResponse {
  class LazyOps(private val resp: () => RestResponse) extends AnyVal {
    def recoverHttpError: RestResponse = try resp() catch {
      case e: HttpErrorException => e.toResponse
    }
  }
  implicit def lazyOps(resp: => RestResponse): LazyOps = new LazyOps(() => resp)

  implicit class TryOps(private val respTry: Try[RestResponse]) extends AnyVal {
    def recoverHttpError: Try[RestResponse] = respTry match {
      case Failure(e: HttpErrorException) => Success(e.toResponse)
      case _ => respTry
    }
  }

  implicit def bodyBasedFromResponse[T](implicit bodyAsReal: AsReal[HttpBody, T]): AsReal[RestResponse, T] =
    AsReal.create(resp => bodyAsReal.asReal(resp.ensure200OK.body))

  implicit def bodyBasedToResponse[T](implicit bodyAsRaw: AsRaw[HttpBody, T]): AsRaw[RestResponse, T] =
    AsRaw.create(value => RestResponse(200, bodyAsRaw.asRaw(value)).recoverHttpError)

  implicit def futureToAsyncResp[T](
    implicit respAsRaw: AsRaw[RestResponse, T]
  ): AsRaw[RawRest.Async[RestResponse], Try[Future[T]]] =
    AsRaw.create { triedFuture =>
      val future = triedFuture.fold(Future.failed, identity)
      callback => future.onCompleteNow(t => callback(t.map(respAsRaw.asRaw).recoverHttpError))
    }

  implicit def futureFromAsyncResp[T](
    implicit respAsReal: AsReal[RestResponse, T]
  ): AsReal[RawRest.Async[RestResponse], Try[Future[T]]] =
    AsReal.create { async =>
      val promise = Promise[T]
      async(t => promise.complete(t.map(respAsReal.asReal)))
      Success(promise.future)
    }
}