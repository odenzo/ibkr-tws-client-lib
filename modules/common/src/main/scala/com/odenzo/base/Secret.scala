package com.odenzo.base

import scala.util.Random
import cats.effect._
import cats.effect.syntax.all._
import cats._
import cats.data._
import cats.syntax.all._
import cats.Show
import _root_.io.circe.Json

import io.circe._



/** Note: User oprint instead of pprint to continue masking */
case class Secret(secret: String) derives Codec.AsObject {
  override def toString = s"${secret.take(2)}...${secret.takeRight(2)}"
}

object Secret  {

  def generatePassword(len: Int = 15): String = Random.alphanumeric.take(len).toList.mkString
  def generate: Secret                        = Secret(generatePassword())
  def generate(len: Int = 15): Secret         = Secret(generatePassword(len))

  //implicit val fooDecoder: Decoder[Secret] = io.circe.derivation.semiauto.deriveDecoder[Secret]

 // implicit  show: Show[Secret]          = Show.fromToString[Secret]
}

case class LoginCreds(user: String, password: Secret)

object LoginCreds {
  def genForUser(user: String) = LoginCreds(user, Secret.generate(15))
}
