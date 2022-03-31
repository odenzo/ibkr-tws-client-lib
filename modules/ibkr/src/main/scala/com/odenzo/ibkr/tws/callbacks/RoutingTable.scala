package com.odenzo.ibkr.tws.callbacks

import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.syntax.all.*
import com.odenzo.ibkr.models.tws.*
import com.odenzo.ibkr.models.tws.SimpleTypes.*
import com.odenzo.ibkr.tws.commands.*

import scala.collection.concurrent.TrieMap
import scala.collection.mutable

/**
 * Usually one per IBWrapper, seperated out for future improvements and experiments. Note this is a mutable Map and we sometimes may leave
 * stray thing in. Some callbacks won't have request id. Currently we are using one per, not one per callback type. Given a specific command
 * we may know what callbacks can be called (?). Currently no "operation" label per ticket so manual fusging and trying to match on
 * sub-type.
 */
class RoutingTable(val clientId: Int) {

  private val routes = TrieMap.empty[RqId, TicketWithId]

  def add(id: RqId, ticket: TicketWithId): IO[Unit] =
    scribe.info(s"Client $clientId Adding Rq $id to routine table")
    IO(routes.putIfAbsent(id, ticket)).void

  def remove(id: RqId): IO[Unit] =
    IO(routes.remove(id)).flatTap {
      case Some(ticket) => IO(scribe.info(s"$clientId: Removed $id and matching Ticket $ticket"))
      case None         => IO(scribe.warn(s"$clientId: Removing $id but not in routing table"))
    }.void

  def clear(id: RqId, ticket: TicketWithId): IO[Unit] =
    scribe.info(s"Client $clientId Adding Rq $id to routine table")
    IO(routes.clear())

  def get(id: RqId): Option[TicketWithId] = routes.get(id)

  def required(id: RqId): Option[TicketWithId] = get(id) match {
    case a @ Some(value) => a
    case b @ None        =>
      scribe.warn(s"Fetching Ticket $id for Client $clientId and was missing")
      b
  }
}
