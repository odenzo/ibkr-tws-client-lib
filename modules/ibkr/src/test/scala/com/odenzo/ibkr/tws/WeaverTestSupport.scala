package com.odenzo.ibkr.tws

import _root_.weaver.*
import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import com.odenzo.ibkr.models.OPrint
import com.odenzo.ibkr.models.OPrint.oprint
import io.circe.Decoder
import org.http4s.client.Client

trait WeaverTestSupport extends TWSTestConfig {

  lazy val clientR: Resource[IO, IBClient] = IBClient.asResource(config)

}
