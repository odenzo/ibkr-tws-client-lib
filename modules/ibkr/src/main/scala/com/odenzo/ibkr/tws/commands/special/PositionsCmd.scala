package com.odenzo.ibkr.tws.commands.special

import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.syntax.all.*
import cats.implicits.*
import com.odenzo.ibkr.models.tws.*
import com.odenzo.ibkr.models.tws.SimpleTypes.*
import com.odenzo.ibkr.tws.IBClient
import com.odenzo.ibkr.tws.commands.*
import com.odenzo.ibkr.tws.models.*
import fs2.concurrent.Topic
import fs2.concurrent.Topic.Closed

/**
 * This i full subcription, need to cancel to stop it. The end callback jut ignal that all position have their initial valuess, further
 * updates will come. Note that normally just one of these is open at a time. You can subscribe to the stream multiple times though. The
 * Defferred latch is closed when the first position end is received. For now the positionend signal is not sent over the topic.
 */
open case class PositionsRq() extends IBRequest {

  def submit()(using client: IBClient): IO[PositionsTicket] = for {
    latch <- Deferred[IO, Boolean]
    topic <- Topic[IO, IBPosition]
    ticket = PositionsTicket(this, topic, latch)
    _     <- client.addPositionsHandler(ticket)
    _      = scribe.info(s"Submitting with  Ticket: ${pprint(ticket)}")
    _      = client.server.reqPositions()

  } yield ticket
}

open class PositionsTicket(val rq: PositionsRq, val topic: Topic[IO, IBPosition], latch: Deferred[IO, Boolean])(using ibClient: IBClient)
    extends TicketWithCancel {

  // TODO: Decide if we only support one Ticket at a time. and if topic is closed we auto-remove that Ticket. Seems better.
  def cancelSubcription(): IO[Unit] = IO {
    ibClient.server.cancelPositions()
    ibClient.clearPositionHandlers() // Not sure if end is current list, or subscription cancelled
  } *> topic.close.void

  /** Typically details per contract, but contract object can match multiple contracts. */
  def position(account: IBAccount, contract: IBContract, pos: BigDecimal, aveCost: Double): IO[Either[Closed, Unit]] =
    scribe.info(s"PositionsTicket:  $contract \n Position: $pos \n AveCost: $aveCost ")
    val v = IBPosition(account, contract, pos, aveCost)
    topic.publish1(v).flatTap {
      case Left(s) => IO(scribe.warn(s"Publiing to closed PubSub ${s}"))
      case _       => IO.unit
    }

  /** When no further contract details are expected from the request */
  def positionEnd(): IO[Boolean] = IO(scribe.info(s"Position End")) *> latch.complete(true)

}
