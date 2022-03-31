package com.odenzo.base

import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import io.circe.*
import io.circe.syntax.*

import java.io.{File, FileOutputStream, OutputStreamWriter}
import java.net.URL
import scala.io.{BufferedSource, Source}

/** Traits for working with Circe Json / DOM
  */
trait CirceUtils extends CatsEffectUtils {

  def json2object(json: Json): IO[JsonObject] =
    optionToError(json.asObject)(OError.of("JSON was not a JSonObject" + json))
  def json2array(json: Json): IO[Vector[Json]] =
    optionToError(json.asArray)(OError.of("JSON was not a Array" + json))
  def json2string(json: Json): IO[String] =
    optionToError(json.asString)(OError.of("JSON was not a String" + json))

  /** Caution: Uses BigDecimal and BigInt in parsing.
    * @param m
    *   The text, in this case the response message text from websocket.
    * @return
    *   JSON or an exception if problems parsing, error holds the original
    *   String.
    */
  def parse(m: String): IO[Json] = {
    val parsed: Either[Throwable, Json] = parser
      .parse(m)
      .leftMap(pf => OError.of("Error Parsing String to Json: $m", pf))
    IO.fromEither(parsed)
  }

  def parseAsJson(f: File): IO[Json] = {
    val res = new io.circe.jawn.JawnParser()
      .parseFile(f)
      .leftMap(pf => OError.of(s"Error Parsing File $f to Json", pf))
    eitherToError(res)
  }

  /** Similar to current jawn.parser.decode[T](jsontext) but exception in IO
    * instead of either
    */
  def parseAndDecode[T: Decoder](m: String): IO[T] = {
    parse(m).flatMap((j: Json) => decode[T](j))
  }

  /** @param json
    *   Well formed Json
    *
    * @return
    *   Parsed json that can be used in other API methods.
    */
  def parseJsonUNSAFE(json: String): Json = {
    io.circe.parser.parse(json) match {
      case Right(j)  => j
      case Left(err) => throw err
    }
  }

  /** To avoid importing io.circe.syntax to use .asJson :-) Also allows
    * explicitly passing in the encoder
    */
  def encode[A](a: A)(implicit enc: Encoder[A]): Json = enc.apply(a)

  /** Easily decode wrapped in our Either AppError style. */
  def decode[A: Decoder](json: Json): IO[A] = IO.fromEither(json.as[A])

  /** Easily decode wrapped in our Either AppError style. */
  def decodeObj[A: Decoder](jobj: JsonObject): IO[A] = eitherToError(
    jobj.asJson.as[A]
  )

  def addField(json: Json)(key: String, v: Json): Json =
    json.asObject match {
      case Some(jo) => jo.add(key, v).asJson
      case _        => json
    }

  def findField(json: JsonObject, fieldName: String): Option[Json] = json(
    fieldName
  )

  def findField(json: Json, fieldName: String): IO[Option[Json]] =
    json2object(json).map(findField(_, fieldName))

  /** Finds the field and decodes. If field not found None returned, if found
    * and decoding error than IO raises an error.
    */
  def findFieldAs[T: Decoder](
      jsonObject: JsonObject,
      fieldName: String
  ): IO[Option[T]] = {
    findField(jsonObject, fieldName).traverse(j => IO.fromEither(j.as[T]))
  }

  /** Finds the field and decodes. If field not found None returned, if found
    * and decoding error than IO raises an error. If json is not a JsonObject
    * will also raise error
    */
  def findFieldAs[T: Decoder](json: Json, fieldName: String): IO[Option[T]] = {
    IO.fromOption(json.asObject)(OError.of("JSON Not a JSONObject"))
      .flatMap(findFieldAs[T](_, fieldName))
  }

  def extractFieldFromObject(jobj: JsonObject, fieldName: String): IO[Json] = {
    IO.fromOption(jobj.apply(fieldName))(
      OError.of(s"Could not Find $fieldName in JSonObject ")
    )
  }

  /** Little utility for common case where an JsonObject just has "key": value
    * WHere value may be heterogenous?
    */
  def extractAsKeyValueList(json: Json): IO[List[(String, Json)]] = {
    IO.fromEither(
      json.asObject
        .toRight(OError.of("JSON Fragment was not a JSON Object"))
        .map(_.toList)
    )
  }

  /** Deep descent through Json to find the first field by name. Returns error
    * if not found, ignores multiple fields by returning only first.
    */
  def extractFirstField(name: String, json: Json): IO[Json] = {
    IO.fromEither(json.findAllByKey(name) match {
      case head :: tail => head.asRight
      case _ =>
        OError
          .of(s"Field $name not found in deep traverse " + json.spaces4)
          .asLeft
    })
  }

  def hasField(name: String, json: Json): IO[Boolean] =
    extractField(name, json).redeem(_ => false, x => true)

  def hasField(name: String, json: JsonObject): Boolean = json(name).isDefined

  /** Finds top level field in the supplied json object */
  def extractField(name: String, json: JsonObject): IO[Json] = {
    extractField(name, json.asJson)
  }

  /** Finds the field name at top level */
  def extractField(name: String, json: Json): IO[Json] = {
    val someJsonField: Option[Json] = json.asObject.flatMap(jo => jo(name))
    com.odenzo.base.IOU
      .required("Field $name not found " + json.spaces4)(someJsonField)
  }

  def extractFieldAs[T: Decoder](name: String, json: Json): IO[T] = {
    extractField(name, json).flatMap(json => IO.fromEither(json.as[T]))
  }

  def extractFieldAs[T: Decoder](name: String, json: JsonObject): IO[T] = {
    extractField(name, json).flatMap(json => IO.fromEither(json.as[T]))
  }

  def loadJsonResource(path: String) = {
    val resource: URL = getClass.getResource(path)
    val source: BufferedSource = Source.fromURL(resource)
    val data: String = source.getLines().mkString("\n")
    this.parse(data)
  }

  /** Construct a Cats Resource with the JSON parsed from the named Java
    * resource
    */
  def makeJsonResource(path: String): Json = {

    val url: URL = getClass.getResource(path)
    val acquire = SyncIO(Source.fromURL(url))
    val resource = Resource.fromAutoCloseable(acquire)

    val json = resource.use { (src: BufferedSource) =>
      val m = src.getLines.mkString
      val parsed: Either[Throwable, Json] = parser
        .parse(m)
        .leftMap(pf => OError.of("Error Parsing String to Json: $m", pf))
      SyncIO.fromEither(parsed)
    }

    json.unsafeRunSync()

  }

  def loadJson(url: URL) = {
    val acquire: IO[BufferedSource] = IO(Source.fromURL(url))
    val rs = Resource.fromAutoCloseable(acquire)
    rs.use { (src) => parse(src.mkString) }
  }

  def writeJson(json: Json, file: File): IO[Unit] = {
    val open = IO(
      new OutputStreamWriter(new FileOutputStream(file, false), "UTF-8")
    )
    Resource
      .fromAutoCloseable(open)
      .use { out =>
        IO(out.write(json.spaces4))
      }
  }

  /** Ripled doesn't like objects like { x=null } and neither does Binary-Codec
    * lib
    * {{{
    *       droppingNullsSortedPrinter.pretty(json)
    * }}}
    */
  val droppingNullsSortedPrinter: Printer =
    Printer.spaces2SortKeys.copy(dropNullValues = true)

  /** This probably doesn't preserve the ordering of fields in Object. */
  def replaceField(
      name: String,
      in: JsonObject,
      withValue: Json
  ): JsonObject = {
    in.remove(name).add(name, withValue)
  }

  /** Does top level sorting of fields in this object alphanumeric with capital
    * before lowercase See if circe sorted fields does this nicely
    */
  def sortFields(obj: JsonObject): JsonObject = {
    JsonObject.fromIterable(obj.toVector.sortBy(_._1))
  }

  /** This does not recurse down, top level fields only */
  def sortFieldsDroppingNulls(obj: JsonObject): JsonObject = {
    val iter = obj.toVector.filter(_._2 =!= Json.Null).sortBy(_._1)
    JsonObject.fromIterable(iter)
  }

  def dropNullValues(obj: JsonObject): Json = obj.asJson.deepDropNullValues

}

object CirceUtils extends CirceUtils
