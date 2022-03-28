package com.odenzo.ibkr.tws.commands

import cats.effect.*
import cats.effect.syntax.all.*

import cats.*
import cats.data.*
import cats.implicits.*

import com.ib.client.Contract
import com.odenzo.ibkr.tws.IBClient
import com.odenzo.ibkr.tws.models.*
import com.odenzo.ibkr.models.tws.*
import com.odenzo.ibkr.models.tws.SimpleTypes.*

open class AccountSummaryTicket(val rqId: RqId, rq: AccountSummaryRq)(using ibClient: IBClient) extends TicketWithId with TicketWithCancel {

  protected val client: IBClient = ibClient

  def accountSummary(account: IBAccount, tag: IBTag, value: String, currency: String): Unit =
    scribe.info(s"CMD: Account Summary Info: ReqID: $rqId $tag $value ")

  /** When no further contract details are expected from the request */
  def accountSummaryEnd(): Unit = scribe.info(s"CMD: AccountSummary End $rqId")

  // This has a cancelAccountSummary command too
  def cancel(): IO[Unit] = IO(client.server.cancelAccountSummary(rqId.toInt))
}

/**
 * @param group
 *   For multiaccounts I guess, I just use "All" for paper trading login with one Account
 * @param tags
 *   AccountSummaryTags enums as CSV?
 */
open case class AccountSummaryRq(group: String = "All", tags: String) {

  def submit()(using client: IBClient): IO[AccountSummaryTicket] = for {
    id: RqId <- client.nextRequestId() // Boilerplate
    ticket = AccountSummaryTicket(id, this)
    _     <- client.addTicket(ticket)
    // val classMethodFn: (IBAccount, IBTag, String, String) => Unit = ticket.accountSummary  // Woohoo, thats new!
    _      = scribe.info(s"Submitting with $id Ticket: ${pprint(ticket)}")
    _      = client.server.reqAccountSummary(id.toInt, group, tags)

  } yield ticket

}
