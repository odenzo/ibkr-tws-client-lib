package com.odenzo.ibkr.tws.callbacks

import cats.effect.IO
import cats.*
import cats.data.*
import cats.syntax.all.*
import com.odenzo.ibkr.models.tws.SimpleTypes.IBAccount

import com.odenzo.ibkr.tws.kernel.*
import com.odenzo.ibkr.tws.commands.special.AccountUpdatesTicket

import scala.collection.concurrent.TrieMap

trait IBWrapperAccountUpdates extends IBWrapperEmpty {

  /* Callbacks for AccountUpdates. Multiple AccountUpdates can be registered, max four I think. Also account can be All.
   * So, a bit messy but designed for general case of All or with two accounts with the TrieMaps.
   * You may just want to parition the stream if dealing with accounts seperately using Al, thought about dealing with each
   * subscription seperately with its own tries etc, but that falls down on IBAccount.ALL
   */

  protected val accountUpdatesHandlers: TrieMap[AccountUpdatesTicket, IBAccount] = TrieMap.empty[AccountUpdatesTicket, IBAccount]

  /** Invokes any callback processing on a different fibre. */
  private def doWithDispatcher(fn: (AccountUpdatesTicket) => IO[Unit]): Unit =
    // Dispatcher[IO].use {   Unless I want to do resource scoped cancellation of subwork not sure the point of Dispatcher
    // dispatcher =>
    import cats.effect.unsafe.implicits.global

    getAllAccountUpdatesHandlers.traverse {
      ticket =>
        scribe.info(s"Traversring Ticket: $ticket")
        IO(scribe.info(s"Dispatching to $ticket")) *> fn(ticket)
    }.void.unsafeRunAndForget()

  /** This has osme special properties, all handlers for ame account. Raises error otherwie */
  def addAccountUpdatesHandler(ticket: AccountUpdatesTicket): IO[Unit] =
    scribe.info(s"Adding AccountUpdateTicket $ticket")
    accountUpdatesHandlers.headOption match {
      case Some(ticket, account) if account != ticket.account => IO.raiseError(new Throwable(s"Account Mismatch $ticket"))
      case _                                                  => IO(accountUpdatesHandlers.update(ticket, ticket.account)).void
    }

  def removeAccountUpdatesHandler(ticket: AccountUpdatesTicket): IO[Unit] = IO(accountUpdatesHandlers.remove(ticket)).void
  def clearAccountHandlers(): IO[Unit]                                    = IO(accountUpdatesHandlers.clear())
  def getAllAccountUpdatesHandlers: List[AccountUpdatesTicket]            = accountUpdatesHandlers.iterator.map(_._1).toList

  override def accountDownloadEnd(accountName: String): Unit =
    scribe.debug(s"accountDownloadEnd $accountName")
    val cb = (ticket: AccountUpdatesTicket) => ticket.accountDownloadEnd(IBAccount(accountName))
    doWithDispatcher(cb)

  override def updateAccountTime(timeStamp: String): Unit =
    scribe.debug(s"updateAccountTime Local Date HH:MM $timeStamp")
    val cb = (ticket: AccountUpdatesTicket) => ticket.updateAccountTime(timeStamp)
    doWithDispatcher(cb)
  //    LocalTime.of()

  override def updateAccountValue(key: String, value: String, currency: String, accountName: String): Unit =
    scribe.debug(s"updateAccountValue $key -> $value $currency $accountName")
    val cb = (ticket: AccountUpdatesTicket) => ticket.updateAccountValue(key, value, currency, IBAccount(accountName)).void
    doWithDispatcher(cb)

  /** AccountUpdates */
  override def updatePortfolio(
      contract: com.ib.client.Contract,
      position: com.ib.client.Decimal,
      marketPrice: Double,
      marketValue: Double,
      averageCost: Double,
      unrealizedPNL: Double,
      realizedPNL: Double,
      accountName: String
  ): Unit =
    scribe.debug(s"UpdatePortfolio $contract $position $marketPrice $marketValue ...")
    val cb = (ticket: AccountUpdatesTicket) =>
      ticket.updatePortfolio(
        contractToIBContract(contract),
        decimalToOptAmount(position).getOrElse(BigDecimal(0.0000)),
        doubleToMoney(marketPrice),
        doubleToMoney(marketValue),
        doubleToMoney(averageCost),
        doubleToMoney(unrealizedPNL),
        doubleToMoney(realizedPNL),
        IBAccount(accountName)
      ).void
    doWithDispatcher(cb)

}
