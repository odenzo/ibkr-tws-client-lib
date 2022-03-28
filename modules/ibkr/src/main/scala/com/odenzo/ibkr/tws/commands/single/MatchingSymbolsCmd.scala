package com.odenzo.ibkr.tws.commands.single

import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.std.{Dispatcher, Queue}
import cats.effect.syntax.all.*
import cats.implicits.*
import com.ib.client.{Contract, ContractDescription, ContractDetails}
import com.odenzo.ibkr.models.tws.*
import com.odenzo.ibkr.models.tws.SimpleTypes.*
import com.odenzo.ibkr.tws.IBClient
import com.odenzo.ibkr.tws.commands.TicketWithId
import com.odenzo.ibkr.tws.models.*

import scala.concurrent.duration.*

/**
 * One off contract not streaming --- In-Bound Thread to submit() is program thread. ib_client request puts on a queue (async/sync not
 * sure). Then the receiver thread from IBWrapper calls the ticket. Might as well have a submitSync() that autatically rendezvous/barrier
 * syncs on the contractDetails callback if really only one. Not sure best Cats Effect for this, CountDownLatch? So far this is the only API
 * that will give zero or one replies. Actually, even if a symbol not matched probably gives exactly one reply with empty list of
 * Descriptions
 */
case class MatchingSymbolsRq(pattern: String) extends SingleStyleRq[Chain[ContractDescription], MatchingSymbolsTicket] {

  def submit()(using client: IBClient): IO[MatchingSymbolsTicket] =
    for {
      id <- client.nextRequestId() // Boilerplate
      ticket = MatchingSymbolsTicket(id, this)
      _     <- client.addTicket(ticket)
      _      = scribe.info(s"Submitting with $id Ticket: ${pprint(ticket)}")
      _      = client.eClientSocket.reqMatchingSymbols(id.toInt, pattern)
    } yield ticket

}

/** This is a finite stream. rqId can */
class MatchingSymbolsTicket(rqId: RqId, rq: MatchingSymbolsRq) extends SingleStyleTicket[Chain[ContractDescription]](rqId) {

  def contractDetails(contractDescriptions: Chain[ContractDescription]): IO[Unit] = setResults(contractDescriptions)

}
