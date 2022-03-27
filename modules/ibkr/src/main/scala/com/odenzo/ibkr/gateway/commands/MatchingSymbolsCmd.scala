package com.odenzo.ibkr.gateway.commands

import cats.effect.*
import cats.effect.syntax.all.*
import cats.*
import cats.data.*
import cats.effect.std.{Dispatcher, Queue}
import cats.implicits.*
import com.ib.client.{Contract, ContractDescription, ContractDetails}
import com.odenzo.ibkr.gateway.IBClient
import com.odenzo.ibkr.gateway.models.*
import com.odenzo.ibkr.gateway.models.SimpleTypes.*

/** You may just want to latest quote, or to get a stream. Experiment with a State instead of mutable var. Callback is symbolSamples */
class MatchingSymbolsTicket(val rqId: RqId, rq: MatchingSymbolsRq) extends TicketWithId {

  private val initialResults = cats.data.Chain.empty[ContractDescription]

  private val results: Deferred[IO, Chain[ContractDescription]] = Deferred.unsafe[IO, Chain[ContractDescription]]

  /**
   * Not sure if this is a one-shot or not. I think so. This introducing a new sync based pattern. May have to use a Dispatcher to call this
   * from non-functional code (e.g. IBWrapper callback) or unsafeRunSync
   */
  def contractDetails(contractDescriptions: Chain[ContractDescription]): IO[Unit] =
    scribe.info(s"CMD:Matching Symbols: ReqID: $rqId  $contractDetails")
    val first: IO[Boolean] = results.complete(contractDescriptions)
    first.ifM(IO.unit, IO(scribe.warn(s"$rqId ${rq.pattern} has subsequence CB")))

  def waitForResults(): IO[Chain[ContractDescription]]         = results.get
  def pollForResults(): IO[Option[Chain[ContractDescription]]] = results.tryGet
}

/**
 * One off contract not streaming --- In-Bound Thread to submit() is program thread. ib_client request puts on a queue (async/sync not
 * sure). Then the receiver thread from IBWrapper calls the ticket. Might as well have a submitSync() that autatically rendezvous/barrier
 * syncs on the contractDetails callback if really only one. Not sure best Cats Effect for this, CountDownLatch? So far this is the only API
 * that will give zero or one replies. Actually, even if a symbol not matched probably gives exactly one reply with empty list of
 * Descriptions
 */
case class MatchingSymbolsRq(pattern: String) {

  def submit()(using client: IBClient, F: Async[IO]): IO[MatchingSymbolsTicket] =
    for {
      id <- client.nextRequestId() // Boilerplate
      ticket = MatchingSymbolsTicket(id, this)
      _     <- client.addTicket(ticket)
      _      = scribe.info(s"Submitting with $id Ticket: ${pprint(ticket)}")
      _      = client.eClientSocket.reqMatchingSymbols(id.toInt, pattern)
    } yield ticket

}
