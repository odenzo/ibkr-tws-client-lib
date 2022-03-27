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

case class AccountValue(key: String, value: String, currency: String, accountName: IBAccount, recorded: Instant = Instant.now)
case class PortfolioItem(
    contact: Contract,
    position: BigDecimal,
    marketPrice: BigDecimal,
    marketValue: BigDecimal,
    averageCost: BigDecimal,
    unrealizedPNL: BigDecimal,
    realizedPNL: BigDecimal,
    accountName: IBAccount
)

/**
 * This is, optionally, subscription based, like positions, but a subcription based on each account. No Req ID Note there is a multi-account
 * equivalent which should use to share Roth and Trading account e.g. There can only be one account subscribed to at a time. Callbacks
 * updateAccountValue, updatePortfolio, updateAccountTime and it seems like an AccountDownloadEnd with the account name once all the account
 * values have been gotten ONCE even with a subscription.
 */
open class AccountUpdatesTicket(val rq: AccountUpdatesRq)(using val client: IBClient, F: Sync[IO]) extends TicketWithCancel {

  // We can put these in mutable maps or can we try putting into FS2 Stream asynchronously?
  // Queue and a Stream that people can subscribe too.

  /** Callback - instead of direct pipe even though only one account can have account update running at once */
  def updateAccountValue(key: String, value: String, currency: String, accountName: IBAccount): Unit =
    scribe.info(s"CMD: AccountUpdates: $key $value $currency $accountName ")
    val item = AccountValue(key, value = value, currency = currency, accountName = accountName, recorded = Instant.now)

  def updatePortfolio(
      contract: Contract,
      position: BigDecimal,
      marketPrice: BigDecimal,
      marketValue: BigDecimal,
      averageCost: BigDecimal,
      unrealizedPNL: BigDecimal,
      realizedPNL: BigDecimal,
      accountName: String
  ): Unit = {
    scribe.info(s"Portfolio Contract Value (which needs a model: $contract $position $marketPrice $marketValue")
    if (accountName != rq.account) scribe.error(s"Expected Account ${rq.account} but got $accountName")
  }

  def updateAccountTime(time: String): Unit =
    scribe.info(s"updated Account Time: $time")

  /** No further contract details are expected from the request, but existing details may be updated. */
  def accountDownloadEnd(account: IBAccount): Unit = scribe.info(s"AccountDownloadEnd $account")

  // This has a cancelAccountSummary command too
  def cancel(): IO[Unit] = IO(client.eClientSocket.reqAccountUpdates(false, rq.account))

  // Some Actual Logic / Action as an example to keep current account state updated for displaying on a UI or something.
  val accountState: mutable.Map[String, AccountValue] = scala.collection.mutable.Map.empty

  /** FS2 Stream based action to update the State. Tempted to try a State variable instead, but leave that for immediate callback */
  val accountStatePipe: Pipe[IO, AccountValue, AccountValue] = stream =>
    stream
      .evalTap(v => IO(scribe.warn(s"Account Mismatch, Got ${v.accountName} Excepted ${rq.account}")))
      .evalTap(v => IO(accountState.put(v.key, v)))
}

/** Request to Start Getting Account Attribute Values and continuous updates (on change)  * */
open case class AccountUpdatesRq(subcribe: Boolean, account: IBAccount) {

  def submit()(using client: IBClient): IO[AccountUpdatesTicket] = for {
    _     <- IO.pure("Cheating")
    ticket = AccountUpdatesTicket(this)
    _      = client.addAccountUpdatesHandler(ticket)
    _      = scribe.info(s"Submitting with Ticket: ${pprint(ticket)}")
    _      = client.server.reqAccountUpdates(subcribe, account)
  } yield ticket

}
