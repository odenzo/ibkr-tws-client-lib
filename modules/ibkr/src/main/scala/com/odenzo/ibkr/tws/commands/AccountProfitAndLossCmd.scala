package com.odenzo.ibkr.tws.commands

import cats.effect.{given, *}
import cats.effect.syntax.all.{given, *}
import cats.*
import cats.data.*
import cats.implicits.*
import com.ib.client.Contract
import com.odenzo.ibkr.tws.IBClient
import com.odenzo.ibkr.tws.models.*
import cats.*
import cats.data.*
import cats.effect.std.{*, given}
import cats.implicits.{*, given}
import fs2.{*, given}
import java.time.Instant
import scala.collection.mutable
import com.odenzo.ibkr.models.tws.*
import com.odenzo.ibkr.models.tws.SimpleTypes.*

case class PnLAccount(dailyPnL: BigDecimal, unrealizedPnL: BigDecimal, realizedPnL: BigDecimal, recorded: Instant = Instant.now)

/** This handles P&L for individual holding as well as at the account level. */
open class AccountProfitAndLossTicket(val rqId: RqId, val rq: AccountProfitAndLossRq)(using ibClient: IBClient)
    extends TicketWithId with TicketWithCancel {

  protected val client: IBClient = ibClient

  // We can put these in mutable maps or can we try putting into FS2 Stream asynchronously?
  // Queue and a Stream that people can subscribe too.

  /** Callback - instead of direct pipe even though only one account can have account update running at once */

  import com.ib.client.EWrapperMsgGenerator

  def pnl(pnl: PnLAccount): Unit = {
    scribe.info(s"Account PNL ${pprint(pnl)}")
  }

  def cancel(): IO[Unit] = IO(client.eClientSocket.cancelPnLSingle(rqId.toInt))
}

/**
 * @param account
 * -- maybe can be All, nor sure Get P&L for account of specific contract/model
 * @param modeCode
 *   Can be "" or something like STK
 * @param contractId
 */
open case class AccountProfitAndLossRq(account: IBAccount, modelCode: Option[String]) extends RqWithId {

  /** Submit can be called to do new requests multiple times, will always create a new RqId */
  def submit()(using client: IBClient): IO[AccountProfitAndLossTicket] = for {
    rqId  <- client.nextRequestId()
    ticket = AccountProfitAndLossTicket(rqId, this)
    _     <- client.addTicket(ticket)
    _      = scribe.info(s"Submitting with Ticket: ${pprint(ticket)}")
    _      = client.eClientSocket.reqPnL(rqId.toInt, account, modelCode.getOrElse(""))

  } yield ticket

}
