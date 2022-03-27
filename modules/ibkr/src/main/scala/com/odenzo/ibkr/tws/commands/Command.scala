package com.odenzo.ibkr.tws.commands

import cats.effect.IO
import com.odenzo.ibkr.models.tws.*
import com.odenzo.ibkr.models.tws.SimpleTypes.*
trait IBRequest
trait RqWithId extends IBRequest

trait Ticket
trait TicketWithCancel extends Ticket {
  def cancel(): IO[Unit]
}
trait TicketWithId     extends Ticket {
  def rqId: RqId
}
