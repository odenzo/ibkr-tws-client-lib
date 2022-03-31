package com.odenzo.ibkr.tws.commands.special

import cats.effect.{IO, Resource}
import com.odenzo.ibkr.models.OPrint
import com.odenzo.ibkr.models.tws.IBPosition
import com.odenzo.ibkr.tws.*

import weaver.IOSuite

import scala.concurrent.duration.*

object PositionsTest extends IOSuite with WeaverTestSupport {

  type Res = IBClient
  def sharedResource: Resource[IO, Res] = clientR
  import scala.language.postfixOps
  test("contractQuote") {
    (c: IBClient, log) =>
      given IBClient = c

      for {
        _      <- IO(scribe.info("ContractQuote"))
        ticket <- PositionsRq().submit()
        all <- doOngoingWork(ticket) <& userSimulation(ticket) // Do in parallel
        _       = scribe.info(s"Received ${all.length}  $all")
        // _ = expect (data.isEmpty)
      } yield success

  }

  def userSimulation(ticket: PositionsTicket): IO[Unit] =
    IO.sleep(10.minute) >> IO(scribe.info("Done sleeping")) *> ticket.cancelSubcription()

  def doOngoingWork(ticket: PositionsTicket): IO[List[IBPosition]] =
    ticket.topic.subscribe(100).debug(OPrint.oprint(_), scribe.info(_)).compile.toList

}
