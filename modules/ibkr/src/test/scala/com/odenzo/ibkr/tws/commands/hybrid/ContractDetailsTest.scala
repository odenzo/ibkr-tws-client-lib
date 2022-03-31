package com.odenzo.ibkr.tws.commands.hybrid

import cats.effect.*
import cats.effect.syntax.all.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import com.ib.client.ContractDescription
import com.odenzo.ibkr.models.OPrint
import com.odenzo.ibkr.models.OPrint.oprint
import com.odenzo.ibkr.models.tws.IBContract
import com.odenzo.ibkr.tws.*
import weaver.IOSuite

import java.util.Currency
import scala.concurrent.duration.*
import com.odenzo.ibkr.tws.models.*
import com.odenzo.ibkr.tws.kernel.*

object ContractDetailsTest extends IOSuite with WeaverTestSupport {

  type Res = IBClient
  def sharedResource: Resource[IO, Res] = clientR
  import scala.language.postfixOps
  test("contractQuote") {
    (c: IBClient, log) =>
      given IBClient = c

      val contract = IBContract(symbol = "NVDA", secType = "STK", currency = Currency.getInstance("USD") /*, exchange = "ISLAND" */ )
        .toWire
      for {
        _      <- IO(scribe.info("ContractQuote"))
        ticket <- ContractDetailsRq(contract).submit()
        closed <- ticket.topic.isClosed
        _      <- IO(scribe.info(s"Got the ticket $closed"))
        all    <- ticket.topic.subscribe(20).debug(OPrint.oprint(_), scribe.info(_)).compile.toList
        _       = scribe.info(s"Received ${all.length}  $all")
        // _ = expect (data.isEmpty)
      } yield success

  }

}
