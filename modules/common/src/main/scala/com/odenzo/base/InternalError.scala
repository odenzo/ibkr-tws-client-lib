package com.odenzo.base

import cats.*
import cats.data.*
import cats.syntax.all.*

import io.circe.Json

object OError {

  def NOT_IMPLEMENTED: Throwable                               = OError.of("Not Implemented")
  def of(t: Throwable): Throwable                              = t
  def of(msg: String): Throwable                               = new Throwable(msg)
  def of(msg: String, cause: Throwable): Throwable             = new Throwable(msg, cause)
  def of(msg: String, json: Json): Throwable                   = new Throwable(msg + s"\n${json.spaces4}")
  def of(msg: String, json: Json, cause: Throwable): Throwable = new Throwable(msg + s"\n${json.spaces4}", cause)

  def of[B](b: Either[Throwable, B]): Either[Throwable, B] = {
    b.leftMap(of("EitherFailed", _))
  }

  def fromOpt[B](b: Option[B], msg: String = "Optional value not present"): Either[Throwable, B] = {
    b.fold(of(msg).asLeft[B])(v => v.asRight)
  }
}
