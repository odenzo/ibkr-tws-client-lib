package com.odenzo.ibkr.tws.commands.subscription

import cats.*
import cats.data.*
import cats.effect.std.{*, given}
import cats.effect.syntax.all.{*, given}
import cats.effect.{*, given}
import cats.implicits.*
import com.ib.client.{Contract, EWrapperMsgGenerator}
import com.odenzo.ibkr.models.OPrint
import com.odenzo.ibkr.models.tws.*
import com.odenzo.ibkr.models.tws.SimpleTypes.*
import com.odenzo.ibkr.tws.IBClient
import com.odenzo.ibkr.tws.commands.*
import com.odenzo.ibkr.tws.models.*
import fs2.concurrent.Topic
import fs2.concurrent.Topic.Closed
import fs2.{*, given}

import java.time.Instant
import scala.collection.mutable

/** This handles P&L for individual holding as well as at the account level. */
open class SingleProfitAndLossTicket(val rqId: RqId, val rq: SingleProfitAndLossRq, topic: Topic[IO, PnLSingle])(using ibClient: IBClient)
    extends TicketWithCancel with TicketWithId {

  override def cancelSubcription(): IO[Unit] = IO(ibClient.server.cancelPnLSingle(rqId.toInt)) *> topic.close.void

  def pnlSingle(rqId: RqId, pnLSingle: PnLSingle): IO[Either[Closed, Unit]] = topic.publish1(pnLSingle)

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
    topic      <- Topic[IO, PnLSingle]
    ticket      = SingleProfitAndLossTicket(rqId, this, topic)
    _           = client.addTicket(ticket)
    _           = scribe.info(s"Submitting with Ticket: ${OPrint.oprint(ticket)}")
    _           = client.server.reqPnLSingle(rqId.toInt, account, modelCode, contractId)
  } yield ticket

}
