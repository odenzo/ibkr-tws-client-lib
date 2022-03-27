package com.odenzo.ibkr.gateway.commands

import cats.effect.{given, *}
import cats.effect.syntax.all.{given, *}
import cats.*
import cats.data.*
import cats.implicits.*
import com.ib.client.Contract
import com.odenzo.ibkr.gateway.IBClient
import com.odenzo.ibkr.gateway.models.*
import com.odenzo.ibkr.gateway.models.SimpleTypes.*
import cats.*
import cats.data.*
import cats.effect.std.{*, given}
import cats.implicits.{*, given}
import fs2.{*, given}

import java.time.Instant
import scala.collection.mutable

import com.ib.client.EWrapperMsgGenerator

case class PnLSingle(
    pos: BigDecimal,
    dailyPnL: BigDecimal,
    unrealizedPnL: BigDecimal,
    realizedPnL: BigDecimal,
    value: BigDecimal,
    recorded: Instant = Instant.now()
)

/** This handles P&L for individual holding as well as at the account level. */
open class SingleProfitAndLossTicket(val rqId: RqId, val rq: SingleProfitAndLossRq)(using ibClient: IBClient) extends TicketWithCancel
    with TicketWithId {

  protected val client: IBClient = ibClient

  def pnlSingle(rqId: RqId, pnLSingle: PnLSingle): Unit = ???
  // This has a cancelAccountSummary command too
  def cancel(): IO[Unit]                                = IO(client.server.cancelPnLSingle(rqId.toInt))

}

/**
 * @param Account
 * -- maybe can be All, nor sure Get P&L for account of specific contract/model
 * @param modeCode
 *   Can be "" or something like STK
 * @param contractId
 */
class SingleProfitAndLossRq(val account: IBAccount, val modelCode: String, val contractId: Int) {

  def submit()(using client: IBClient): IO[SingleProfitAndLossTicket] = for {
    rqId: RqId <- client.nextRequestId()
    ticket      = SingleProfitAndLossTicket(rqId, this)
    _           = client.addTicket(ticket)
    _           = scribe.info(s"Submitting with Ticket: ${pprint(ticket)}")
    _           = client.server.reqPnLSingle(rqId.toInt, account, modelCode, contractId)
  } yield ticket

}
