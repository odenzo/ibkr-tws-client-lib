package com.odenzo.ibkr.gateway.commands

import cats.effect.IO
import com.odenzo.ibkr.gateway.models.SimpleTypes.*
trait IBRequest
trait RqWithId extends IBRequest

trait Ticket
trait TicketWithCancel extends Ticket {
  def cancel(): IO[Unit]
}
trait TicketWithId     extends Ticket {
  def rqId: RqId
}
