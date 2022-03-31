package com.odenzo.ibkr.tws.commands.hybrid

import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.std.Queue
import cats.effect.syntax.all.*
import cats.implicits.*
import com.ib.client.{Contract, ContractDetails}
import com.odenzo.ibkr.models.tws.*
import com.odenzo.ibkr.models.tws.SimpleTypes.*
import com.odenzo.ibkr.tws.IBClient
import com.odenzo.ibkr.tws.commands.TicketWithId
import com.odenzo.ibkr.tws.models.*
import fs2.*
import fs2.concurrent.Topic
import fs2.concurrent.Topic.Closed

/** Not sure why this has info/end paradigm, only evey get one. Try a Topic instead of Queue even though most likely a single subcriber */
class ContractDetailsTicket(val rqId: RqId, rq: Contract, val topic: Topic[IO, ContractDetails]) extends TicketWithId {

  private val initialResults = cats.data.Chain.empty[ContractDetails]

  // private val results = topic.subscribe(20).compile.toList

  /** Typically details per contract, but contract object can match multiple contracts */
  def contractDetails(contractDetails: com.ib.client.ContractDetails): IO[Either[Closed, Unit]] =
    scribe.info(s"CMD: Contract Details: ReqID: $rqId Topic: ${topic.isClosed}")
    topic.publish1(contractDetails)

  /** When no further contract details are expected from the request */
  def contractDetailsEnd(): IO[Either[Closed, Unit]] =
    scribe.info(s"CMD: Contract Details End for ReqID  $rqId")
    topic.close

}

/** One off contract quote, not streaming */
case class ContractDetailsRq(contract: Contract) {

  def submit()(using client: IBClient): IO[ContractDetailsTicket] = for {
    id <- client.nextRequestId() // Boilerplate
    topic <- Topic[IO, ContractDetails]
    ticket = ContractDetailsTicket(id, contract, topic)
    _     <- client.addTicket(ticket)
    _      = scribe.info(s"Submitting with $id Ticket: ${pprint(ticket)}")
    _      = client.server.reqContractDetails(id.toInt, contract)
  } yield ticket

}
