package com.odenzo.ibkr.tws.commands.special

import cats.effect.{IO, Resource}
import com.odenzo.ibkr.models.OPrint
import com.odenzo.ibkr.models.tws.SimpleTypes.IBAccount
import com.odenzo.ibkr.tws.*
import weaver.IOSuite
import scala.concurrent.duration.*
object AccountUpdateTest extends IOSuite with WeaverTestSupport {

  type Res = IBClient
  def sharedResource: Resource[IO, Res] = clientR
  import scala.language.postfixOps
  test("accountUpdate") {
    (c: IBClient, log) =>
      given IBClient = c

      for {
        _      <- IO(scribe.info("accountUpdate"))
        ticket <- AccountUpdatesRq(IBAccount("All")).submit()
        all <- doOngoingWork(ticket) <& userSimulation(ticket) // Do in parallel
        _       = scribe.info(s"Received ${all.length}  $all")
        // _ = expect (data.isEmpty)
      } yield success

  }

  def userSimulation(ticket: AccountUpdatesTicket): IO[Unit] =
    val delay = 5.minutes
    IO.sleep(delay) >> IO(scribe.info(s"Done sleeping $delay")) *> ticket.cancelSubcription()

  def doOngoingWork(ticket: AccountUpdatesTicket): IO[List[IBAccountUpdateMsg]] =
    ticket.topic.subscribe(40).debug("STREAM: " + OPrint.oprint(_), scribe.info(_)).compile.toList

}
