package com.odenzo.base

import cats.effect._
import cats.effect.syntax.all._
import cats._
import cats.data._
import cats.syntax.all._
import cats.effect._
import com.odenzo.base._
import io.circe.syntax.EncoderOps
import io.circe.{Json, JsonObject}

import java.io.File
import java.nio.charset.Charset
import scala.io.Codec
import io.circe._
import io.circe.syntax._

/** Mozilla SOPS encryption and decryption helpers
  * To edit in place sops -
  */
object SOPS extends CirceUtils {

  val sopsPath = "/usr/local/bin/sops"

  /** Encrypts the JSON and writes to file, possible raising an error with IO context */
  def encrypt(json: Json, toFile: File): IO[Unit] =
    IO.delay {
      val data: Array[Byte] = json.spaces4.getBytes(Charset.forName("UTF-8"))
      os
        .proc(Seq(sopsPath, "--input-type", "json", "--output-type", "json", "-e", "/dev/stdin"))
        .call(stdin = data, check = true)

    }.flatMap { res => InputOutputFiles.withOutputStream(toFile).use { out => IO(out.write(res.out.bytes)) } }

  def decryptJson(input: os.ResourcePath): IO[Any] = {
    IO {
      os.proc(Seq(sopsPath, "--input-type", "json", "--output-type", "json", "-d", "/dev/stdin"))
        .call(stdin = input.toSource, check = true)
        .out
        .text(Codec.UTF8)
    }.flatMap(t => parse(t))
  }

  def decryptJson(fromFile: File): IO[Json] = {
    IO.delay {
      os
        .proc(Seq(sopsPath, "--input-type", "json", "--output-type", "json", "-d", fromFile.getCanonicalPath))
        .call(check = true)
        .out
        .text(Codec.UTF8)
    }.flatMap{ txt => IO.fromEither(io.circe.parser.parse(txt)) }
  }

  def decryptYaml(fromFile: File): IO[Json] = {
    val txt: String = os
      .proc(Seq(sopsPath, "--input-type", "yaml", "--output-type", "json", "-d", fromFile.getCanonicalPath))
      .call(check = true)
      .out
      .text(Codec.UTF8)

    parse(txt)
    

  }

//  /** SOPS files may have keys with _unencrypted to denote don't encrypt :-)
//    * You can strip out the suffix from all keys containing using this throughout the whole JSON tree.
//    */
//  def modifyFieldNamesStrippingUnencrypted(json: Json): Json = {
//
//    import monocle.function.Plated
//    // There is probably a better way to do this, but Field and the 'key' of (key,val) doesn't seem to be exposed by optics.
//    // So, I guess this is kinda iteree ish
//    val suffiex   = "_unencrypted"
//    val suffixLen = suffiex.length
//    Plated.transform[Json] { j =>
//      j.asObject match {
//        case None     => j
//        case Some(jo) =>
//          val stripped = jo.toList.map {
//            case (k, v) if k.endsWith(suffiex) => (k.dropRight(suffixLen), v)
//            case (k, v)                        => (k, v)
//          }
//          JsonObject.fromIterable(stripped).asJson
//      }
//    }(json)
//  }
}
