package com.odenzo.ibkr.tws.commands.special

import cats.*
import cats.data.*
import cats.effect.std.{*, given}
import cats.effect.syntax.all.{*, given}
import cats.effect.{*, given}
import cats.syntax.all.*

import com.odenzo.ibkr.models.tws.*
import com.odenzo.ibkr.models.tws.SimpleTypes.*
import com.odenzo.ibkr.tws.IBClient
import com.odenzo.ibkr.tws.commands.*
import com.odenzo.ibkr.tws.models.*
import fs2.*
import fs2.concurrent.Topic
import fs2.concurrent.Topic.Closed

import java.time.Instant
import scala.collection.concurrent.TrieMap
import scala.collection.mutable

type IBAccountUpdateMsg = IBAccountSummaryTag | IBPortfolioPosition | IBAccountDownloadEnded | IBAccountTime

/** Request to Start Getting Account Attribute Values and continuous updates (on change)  * */
open case class AccountUpdatesRq(account: IBAccount) {

  def submit()(using client: IBClient): IO[AccountUpdatesTicket] = for {
    topic <- Topic[IO, IBAccountUpdateMsg]
    latch <- Deferred[IO, Boolean]
    ticket = AccountUpdatesTicket(this, topic, latch)
    _     <- client.addAccountUpdatesHandler(ticket)
    _      = scribe.info(s"Submitting with Ticket: ${pprint(ticket)}")
    _      = client.server.reqAccountUpdates(true, account)
  } yield ticket

}

/**
 * This gets Account and Portfolio information. It constantly updates a TriMap with the data. It also published the inbound data on a PubSub
 * Topic. (Should make this optionally really). And each data object carries a UTC timestamp of when it was created.
 *   - Four message types are sent over the Topic:
 *     - IBAccountSummaryTag : One piece of account data
 *     - IBPortfolioPosition : Info for each Contract in an account
 *     - AccountUpdateTime : Last time an update was done (not sure how this works for partial updated after AccountUpdateEnd
 *     - AccountDownloadEnd :-- All account values downl
 *
 * @param rq
 *   Original request that created this ticket.
 * @param topic
 *   PubSub Topic to subscribe to and ensure it is drained. Use cancelSubscription() to stop publisher (chain)
 * @param deferredAccountComplete
 *   This is set to true as soon as all the account information is completed (AccountDownloadEnded). The account and portfolio state should
 *   be fully populated at this point. Prior they will be partially populated. After they will continue being updated to reflect new
 *   conditions/changes until the subscription is canceled.
 */
open class AccountUpdatesTicket(
    val rq: AccountUpdatesRq,
    val topic: Topic[IO, IBAccountUpdateMsg],
    deferredAccountComplete: Deferred[IO, Boolean] // More like a Latch but Countdown Latch has no attempt, tryGet or get
)(using val ibClient: IBClient)
    extends TicketWithCancel {
  // Some Actual Logic /
  // Some tags repeat based on currency
  private val accountState   = TrieMap.empty[(IBAccount, IBTag), IBAccountSummaryTag]
  private val portfolioState = TrieMap.empty[(IBAccount, IBContract), IBPortfolioPosition]

  /** Multithread safe, but if called before the "END" notification may be incomplete */
  def uniqueAccounts: Set[IBAccount] = accountState.keys.map(_._1).toSet

  def uniqueContractsInAccount(acct: IBAccount): collection.Set[IBContract] = portfolioState.keySet.map(_._2)

  /** FS2 Stream based action to update the State. Tempted to try a State variable instead, but leave that for immediate callback */
  def account: IBAccount = rq.account

  def cancelSubcription(): IO[Unit] =
    IO(ibClient.server.reqAccountUpdates(false, rq.account)).void *> topic.close.void

  // We can put these in mutable maps or can we try putting into FS2 Stream asynchronously?
  // Queue and a Stream that people can subscribe too.

  /** Callback - instead of direct pipe even though only one account can have account update running at once */
  def updateAccountValue(key: String, value: String, currency: String, accountName: IBAccount): IO[Either[Closed, Unit]] =
    scribe.info(s"CMD: AccountUpdates: $key $value $currency $accountName ")
    val data = IBAccountSummaryTag(account, IBTagValue.from(IBTag(key), value, currency))
    accountState.update((data.account, data.tv.tag), data)
    topic.publish1(data)

  /** Callback */
  def updatePortfolio(
      contract: IBContract,
      position: BigDecimal,
      marketPrice: BigDecimal,
      marketValue: BigDecimal,
      averageCost: BigDecimal,
      unrealizedPNL: BigDecimal,
      realizedPNL: BigDecimal,
      accountName: IBAccount
  ): IO[Either[Closed, Unit]] = {
    scribe.info(s"Portfolio Contract Value (which needs a model: $contract $position $marketPrice $marketValue")
    if (accountName != rq.account) scribe.error(s"Expected Account ${rq.account} but got $accountName")
    val data = IBPortfolioPosition(
      contract = contract,
      position = position,
      marketPrice = marketPrice,
      marketValue = marketValue,
      averageCost = averageCost,
      unrealizedPNL = unrealizedPNL,
      realizedPNL = realizedPNL,
      account = accountName
    )
    portfolioState.update((data.account, data.contract), data)
    topic.publish1(data)
  }

  def updateAccountTime(time: String): IO[Unit] =
    IO(scribe.info(s"updated Account Time: $time"))

  /** No further (different) detail types are expected from the request, but existing details values may be updated. */
  def accountDownloadEnd(account: IBAccount): IO[Unit] =
    IO(scribe.info(s"AccountDownloadEnd $account")) *>
      deferredAccountComplete.complete(true) *>
      topic.publish1(IBAccountDownloadEnded(account)).void

}
