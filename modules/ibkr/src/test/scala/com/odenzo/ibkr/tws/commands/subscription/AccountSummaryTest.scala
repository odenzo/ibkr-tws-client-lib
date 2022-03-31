package com.odenzo.ibkr.tws.commands.subscription

import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import com.ib.client.ContractDescription
import com.odenzo.ibkr.models.OPrint
import com.odenzo.ibkr.models.OPrint.oprint
import com.odenzo.ibkr.models.tws.{IBAccountSummaryTag, IBContract}
import com.odenzo.ibkr.tws.*
import com.odenzo.ibkr.tws.commands.subscription.*
import com.odenzo.ibkr.tws.models.*
import weaver.{Expectations, IOSuite}

import java.util.Currency
import scala.concurrent.duration.*

object AccountSummaryTest extends IOSuite with WeaverTestSupport {

  type Res = IBClient
  def sharedResource: Resource[IO, Res] = clientR
  import scala.language.postfixOps
  test("accountSummary") {
    (c: IBClient, log) =>
      given IBClient = c

      for {
        _      <- IO(scribe.info("accountSummary"))
        ticket <- AccountSummaryRq().submit()
        all <- doOngoingWork(ticket) <& userSimulation(ticket) // Do in parallel
        _       = scribe.info(s"Received ${all.length}  $all")
        // _ = expect (data.isEmpty)
      } yield success

  }

  def userSimulation(ticket: AccountSummaryTicket): IO[Unit] =
    val delay = 5.minutes
    IO.sleep(delay) >> IO(scribe.info(s"Done sleeping $delay")) *> ticket.cancelSubcription()

  def doOngoingWork(ticket: AccountSummaryTicket): IO[List[IBAccountSummaryTag]] =
    ticket.topic.subscribe(40).debug("STREAM: " + OPrint.oprint(_), scribe.info(_)).compile.toList

}
