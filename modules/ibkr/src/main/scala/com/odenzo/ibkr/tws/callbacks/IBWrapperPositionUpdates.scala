package com.odenzo.ibkr.tws.callbacks

import cats.*
import cats.data.*
import cats.syntax.all.*
import cats.effect.kernel.Outcome
import cats.effect.{IO, Ref}
import com.ib.client.EWrapper
import com.odenzo.ibkr.models.tws.SimpleTypes.IBAccount
import com.odenzo.ibkr.tws.commands.special.PositionsTicket
import com.odenzo.ibkr.tws.kernel.*

/**
 * Stateful extension of the EWrapper callback. For ReqId based callbacks this sends reponses to registered (id -> ticket) ticket. When no
 * rqId, e.g. positions, a list of current subscription is given. In future I may have one stream to subscribe also/instead.
 */
trait IBWrapperPositionUpdates extends EWrapper {
  import cats.effect.unsafe.implicits.global

  /** Note that its now possible to store a class function as a value, should use that for generality? But have multiple fns so don't. */
  private val handler: Ref[IO, Option[PositionsTicket]] = Ref.unsafe[IO, Option[PositionsTicket]](None)

  /** Sets the handler if not already set, otherwise throws error */
  private def updateHandler(b: PositionsTicket)(a: Option[PositionsTicket]): Option[PositionsTicket] = a match {
    case None    => b.some
    case Some(a) => throw Throwable(s"Handler Already Set to $a")
  }

  /** Clears the handler even if already cleared. */
  private def clearHandler(a: Option[PositionsTicket]) = None

  private def getHandler(): IO[Option[PositionsTicket]] = handler.get

  /**
   * Everybody that asks for a position will get ALL the positions. Perhap a Topic here is better and then people can apply filter.
   * Currently the responses wont have a request id or handle to who requested.
   */

  // No concurrent Set or lisst
  private val positionsHandlers = scala.collection.concurrent.TrieMap.empty[PositionsTicket, PositionsTicket]

  /** This will receive positions for all the handlers regitered. */
  def addPositionsHandler(ticket: PositionsTicket): IO[Unit]    = handler.update(updateHandler(ticket))
  def removePositionsHandler(ticket: PositionsTicket): IO[Unit] = clearPositionHandlers()
  def clearPositionHandlers(): IO[Unit]                         = handler.update(clearHandler)

  override def position(account: String, contract: com.ib.client.Contract, pos: com.ib.client.Decimal, avgCost: Double): Unit =
    scribe.info(s"WRAPPER: Position $account ${pprint(contract)} $pos $avgCost")
    getHandler().flatMap {
      case None     => IO.raiseError(Throwable("No Hanlder Registered but got message"))
      case Some(cb) => cb.position(IBAccount(account), contractToIBContract(contract), pos.value, avgCost)
    }.flatMap {
      case Left(closed) => clearPositionHandlers()
      case Right(value) => IO.unit
    }.unsafeRunAsyncOutcome {
      case Outcome.Succeeded(a) => ()
      case Outcome.Errored(e)   => scribe.error("PositionUpdate Error", e)
      case Outcome.Canceled()   => scribe.warn(s"Update Cancelled")
    }

  /** Dispatches to all registered position handlers and then clears registered handlers */
  override def positionEnd(): Unit =
    scribe.info(s"WRAPPER positionEnd")
    val prog: IO[Boolean] = getHandler().flatMap {
      case None     => IO.raiseError(Throwable("No Hanlder Registered but got message"))
      case Some(cb) => cb.positionEnd()
    }
    runAsync(prog, "PositionEnd")

  private def runAsync[T](prog: IO[T], msg: String = "[No Name]"): Unit =
    prog.unsafeRunAsyncOutcome {
      case Outcome.Succeeded(a) => scribe.info(s"$msg: Succeeded: $a")
      case Outcome.Errored(e)   => scribe.error(s"$msg: Error", e)
      case Outcome.Canceled()   => scribe.warn(s"$msg:  Cancelled")
    }

}
