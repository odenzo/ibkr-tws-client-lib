package com.odenzo.ibkr.tws.commands.subscription

import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.syntax.all.*
import cats.implicits.*
import com.ib.client.Contract
import com.ib.controller.AccountSummaryTag
import com.odenzo.ibkr.models.tws.*
import com.odenzo.ibkr.models.tws.SimpleTypes.*
import com.odenzo.ibkr.tws.IBClient
import com.odenzo.ibkr.tws.commands.*
import com.odenzo.ibkr.tws.models.*
import fs2.concurrent.Topic
import fs2.concurrent.Topic.Closed

open class AccountSummaryTicket(val rqId: RqId, val rq: AccountSummaryRq, val topic: Topic[IO, IBAccountSummaryTag])(using
ibClient: IBClient)
    extends TicketWithId
    with TicketWithCancel {

  def accountSummary(account: IBAccount, tag: IBTag, value: String, currency: String): IO[Unit] =
    scribe.info(s"CMD: Account Summary Info: ReqID: $rqId $tag $value $currency ")
    val m = IBAccountSummaryTag(account, IBTagValue.from(tag, value, currency))
    topic.publish1(m).void

  /** When no further contract details are expected from the request */
  def accountSummaryEnd(): IO[Unit] = IO(scribe.info(s"CMD: AccountSummary End $rqId"))

  // This has a cancelAccountSummary command too
  def cancelSubcription(): IO[Unit] = IO(ibClient.server.cancelAccountSummary(rqId.toInt)) *> topic.close.void
}

/**
 * @param group
 *   For multiaccounts I guess, I just use "All" for paper trading login with one Account
 * @param tags
 *   AccountSummaryTags cannot find. enums as CSV?
 */
open case class AccountSummaryRq(group: String = "All", tags: String = AccountSummaryRq.all) {

  def submit()(using client: IBClient): IO[AccountSummaryTicket] = for {
    id: RqId <- client.nextRequestId() // Boilerplate
    topic <- Topic[IO, IBAccountSummaryTag]
    ticket = AccountSummaryTicket(id, this, topic)
    _     <- client.addTicket(ticket)
    // val classMethodFn: (IBAccount, IBTag, String, String) => Unit = ticket.accountSummary  // Woohoo, thats new!
    _      = scribe.info(s"Submitting with $id Ticket: ${pprint(ticket)}")
    _      = client.server.reqAccountSummary(id.toInt, group, tags)

  } yield ticket

}

object AccountSummaryRq:
  val all: String = AccountSummaryTag.values().map(_.name()).mkString(",")
  val ledgerAll   = "$LEDGER:ALL"
