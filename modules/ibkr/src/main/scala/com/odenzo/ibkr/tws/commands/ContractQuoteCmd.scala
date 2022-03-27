package com.odenzo.ibkr.tws.commands

import cats.effect.*
import cats.effect.syntax.all.*
import cats.*
import cats.data.*
import cats.effect.std.Queue
import cats.implicits.*
import com.ib.client.{Contract, ContractDetails}
import com.odenzo.ibkr.tws.IBClient
import com.odenzo.ibkr.tws.models.*
import com.odenzo.ibkr.models.tws.*
import com.odenzo.ibkr.models.tws.SimpleTypes.*

/** You may just want to latest quote, or to get a stream. Experiment with a State instead of mutable var. */
class ContractQuoteTicket(val rqId: RqId, rq: Contract) extends TicketWithId {

  private val initialResults = cats.data.Chain.empty[ContractDetails]

  private val results = Ref.unsafe[IO, Chain[ContractDetails]](Chain.empty[ContractDetails])

  /** Typically details per contract, but contract object can match multiple contracts */
  def contractDetails(contractDetails: com.ib.client.ContractDetails): Unit =
    scribe.info(s"CMD: Contract Details: ReqID: $rqId  $contractDetails")

  /** When no further contract details are expected from the request */
  def contractDetailsEnd(): Unit = scribe.info(s"CMD: Contract Details End $rqId")

}

/** One off contract quote, not streaming */
case class ContractQuoteRq(contract: Contract) {

  def submit()(using client: IBClient): IO[ContractQuoteTicket] = for {
    id <- client.nextRequestId() // Boilerplate
    ticket = ContractQuoteTicket(id, contract)
    _     <- client.addTicket(ticket)
    _      = scribe.info(s"Submitting with $id Ticket: ${pprint(ticket)}")
    _      = client.server.reqContractDetails(id.toInt, contract)
  } yield ticket

}
