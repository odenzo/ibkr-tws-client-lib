package com.odenzo.ibkr.tws.commands.subscriptions

import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.syntax.all.*
import cats.implicits.*
import com.ib.client.Contract
import com.odenzo.ibkr.models.tws.*
import com.odenzo.ibkr.models.tws.SimpleTypes.*
import com.odenzo.ibkr.tws.IBClient
import com.odenzo.ibkr.tws.commands.*
import com.odenzo.ibkr.tws.models.*

/**
 * @param group
 *   For multiaccounts I guess, I just use "All" for paper trading login with one Account
 * @param tags
 *   AccountSummaryTags enums as CSV?
 */
open case class PositionsRq() extends IBRequest {

  def submit()(using client: IBClient): IO[PositionsTicket] = for {
    _     <- IO.pure("Fake")
    ticket = PositionsTicket(this)
    _      = client.addPositionsHandler(ticket)
    _      = scribe.info(s"Submitting with  Ticket: ${pprint(ticket)}")
    _      = client.server.reqPositions()

  } yield ticket
}

open class PositionsTicket(val rq: PositionsRq)(using ibClient: IBClient) extends TicketWithCancel {

  protected val client: IBClient = ibClient

  /** Typically details per contract, but contract object can match multiple contracts. */
  def position(account: IBAccount, contract: Contract, pos: BigDecimal, aveCost: Double): Unit =
    scribe.info(s"PositionsTicket:  $contract \n Position: $pos \n AveCost: $aveCost ")

  /** When no further contract details are expected from the request */
  def positionEnd(): Unit = scribe.info(s"PositionsTicket End")

  // This has a cancelAccountSummary command too
  def cancel(): IO[Unit] = IO {
    client.server.cancelPositions() // On Cancel Positions all PositionHandlers are cleared automatically
  }
}
