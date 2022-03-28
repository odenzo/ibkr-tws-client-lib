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
trait SingleStyleRq[U, T <: SingleStyleTicket[_]] {}

trait SingleStyleTicket[U](val rqId: RqId) extends TicketWithId {
  private val results: Deferred[IO, U] = Deferred.unsafe[IO, U]

  protected def setResults(v: U): IO[Unit] =
    scribe.info(s"CMD:Matching Symbols: ReqID: $rqId  $v")
    results.complete(v).void

  def waitForResults(): IO[U]         = results.get
  def pollForResults(): IO[Option[U]] = results.tryGet
}
