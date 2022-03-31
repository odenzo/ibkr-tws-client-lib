package com.odenzo.ibkr.tws.commands.subscription

import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.std.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import com.ib.client.Contract
import com.odenzo.ibkr.models.tws.*
import com.odenzo.ibkr.models.tws.SimpleTypes.*
import com.odenzo.ibkr.tws.IBClient
import com.odenzo.ibkr.tws.commands.*
import com.odenzo.ibkr.tws.models.*
import fs2.*
import fs2.concurrent.Topic
import fs2.concurrent.Topic.Closed

import java.time.Instant
import scala.collection.mutable

/**
 * @param account
 * -- maybe can be All, nor sure Get P&L for account of specific contract/model
 * @param modeCode
 *   Can be "" or something like STK
 * @param contractId
 */
open case class AccountProfitAndLossRq(account: IBAccount, modelCode: Option[String]) {

  /** Submit can be called to do new requests multiple times, will always create a new RqId */
  def submit()(using client: IBClient): IO[AccountProfitAndLossTicket] = for {
    rqId  <- client.nextRequestId()
    topic <- Topic[IO, PnLAccount]
    ticket = AccountProfitAndLossTicket(rqId, this, topic)
    _      = client.addTicket(ticket)
    _      = client.server.reqPnL(rqId.toInt, account, modelCode.getOrElse(""))
  } yield ticket
}

/** This handles P&L for individual holding as well as at the account level. */
open class AccountProfitAndLossTicket(val rqId: RqId, val rq: AccountProfitAndLossRq, topic: Topic[IO, PnLAccount])(using
ibClient: IBClient) extends TicketWithId with TicketWithCancel {

  def cancelSubcription(): IO[Unit] = IO(ibClient.server.cancelPnL(rqId.toInt)) *> ibClient.removeTicket(rqId) *> topic.close.void

  /**
   * Callback when new Account PnL arrives
   * @param pnl
   */
  def pnl(pnl: PnLAccount): IO[Either[Closed, Unit]] =
    scribe.info(s"Account PNL ${pprint(pnl)}")
    topic.publish1(pnl)

}
