package com.odenzo.ibkr.tws

import com.odenzo.ibkr.tws.commands.*

import com.odenzo.ibkr.models.tws.*
import com.odenzo.ibkr.models.tws.SimpleTypes.*
import scala.collection.mutable
import com.odenzo.ibkr.models.tws.*
import com.odenzo.ibkr.models.tws.SimpleTypes.*

/**
 * Usually one per IBWrapper, seperated out for future improvements and experiments. Note this is a mutable Map and we sometimes may leave
 * stray thing in. Some callbacks won't have request id. Currently we are using one per, not one per callback type. Given a specific command
 * we may know what callbacks can be called (?). Currently no "operation" label per ticket so manual fusging and trying to match on
 * sub-type.
 */
class RoutingTable(val clientId: Int) {

  private val routes: mutable.Map[RqId, TicketWithId] = scala.collection.mutable.Map.empty

  def add(id: RqId, ticket: TicketWithId): Unit =
    scribe.info(s"Client $clientId Adding Rq $id to routine table")
    routes.addOne(id -> ticket)
  def remove(id: RqId): Option[TicketWithId]    =
    val ticket = routes.remove(id)
    ticket match {
      case Some(ticket) => scribe.info(s"$clientId: Removed $id and matching Ticket $ticket")
      case None         => scribe.warn(s"$clientId: Removing $id but not in routing table")
    }
    ticket

  def get(id: RqId): Option[TicketWithId] = routes.get(id)

  def required(id: RqId): Option[TicketWithId] = get(id) match {
    case a @ Some(value) => a
    case b @ None        =>
      scribe.warn(s"Fetching Ticket $id for Client $clientId and was missing")
      b
  }
}
