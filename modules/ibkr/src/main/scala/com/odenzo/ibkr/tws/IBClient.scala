package com.odenzo.ibkr.tws

import com.ib.client.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import cats.effect.syntax.all.*
import cats.effect.*
import com.odenzo.ibkr.tws.commands.*
import com.odenzo.ibkr.models.tws.*
import com.odenzo.ibkr.models.tws.SimpleTypes.*
import com.odenzo.ibkr.tws.callbacks.IBWrapper
import com.odenzo.ibkr.tws.commands.single.*
import com.odenzo.ibkr.tws.commands.hybrid.*
import com.odenzo.ibkr.tws.commands.subscription.*

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.{lang, util}

/**
 * This should be used as a Cats Resource. We are assuming ASCII-7 for client. Go figure that IB doesn't use UTF=8 still? This is passed
 * around, and should have unique clientId and has its own sequence of ReqID
 */
class IBClient(val config: IBClientConfig, val wrapper: IBWrapper, val eClientSocket: EClientSocket)(using val F: Async[IO]) {
  import cats.implicits.*
  private var connected: AtomicBoolean = new AtomicBoolean(false)
  private var requestId: AtomicInteger = new AtomicInteger(1)

  def nextRequestId(): IO[RqId] = F.blocking(RqId(requestId.getAndIncrement()))

  final inline def server: EClientSocket = eClientSocket
  export wrapper.{
    add => addTicket,
    remove => removeTicket,
    addPositionsHandler,
    clearPositionHandlers,
    addAccountUpdatesHandler,
    removeAccountUpdatesHandler
  }

}

object IBClient:
  private var clientId: AtomicInteger = new AtomicInteger(1)

  /** TODO: Ensure in range 1...32 so really we need a Map of these and close on client close */
  def nextClientId: Int = clientId.getAndIncrement()

  def setup(config: IBClientConfig): IO[IBClient] = {
    for {
      _        <- IO(scribe.info(s"Setting Up Client: ${pprint(config)}"))
      clientId  = config.clientId
      wrapper <- IO.delay(new IBWrapper(clientId))                     // Ready the callback from IB SDK, needed to create the
      // EClientSocket
      socketSignal <- connect(wrapper, config)                         // Synchronous IB Gateway Connection and Setup
      (eClientSocket, eSignal) = socketSignal                          // I thought I was supposwe to be able to do on flatmap?
      eReader  <- IO.delay(new EReader(eClientSocket, eSignal))
      consumer <- consumerFibre(eClientSocket, eSignal, eReader).start // Spawn a thread from out current "program" thread
      producer <- producerThread(eClientSocket, eReader)               // Not this is kinda eager (on IO unsaferun)
      ibClient <- IO.delay(new IBClient(config, wrapper, eClientSocket))
    } yield ibClient
  }

  /** Create Socket Handlers and connect to IB Gateway or IB TWS *synchrously */
  protected def connect(wrapper: EWrapper, config: IBClientConfig): IO[(EClientSocket, EJavaSignal)] =
    IO {
      val eSignal: EJavaSignal   = new EJavaSignal()
      val eClient: EClientSocket = new EClientSocket(wrapper, eSignal)
      val conn                   = config.gateway
      eClient.setAsyncEConnect(false) // Sync, so we are connected when completed callback/ACK just for info
      eClient.eConnect(conn.host, conn.port, conn.clientId)
      (eClient, eSignal)
    }.handleError {
      case e: IllegalArgumentException =>
        scribe.error("Error Connecting", e)
        throw e
      case e: Exception                =>
        scribe.error("Error Connecting", e)
        throw e
    }

  // Now I want to spawn a backgrounded thread, which is scoped on EReader/EClient, not sure yet so return the Fibre
  protected def consumerFibre(eClient: EClientSocket, eSignal: EJavaSignal, eReader: EReader): IO[Unit] = IO {
    while (eClient.isConnected) {
      // scribe.info("Waiting for Reader Signal")
      eSignal.waitForSignal() // This can throw Interrupted Exception.
      // scribe.info("Got Signal")
      eReader.processMsgs()
    }
  }.handleErrorWith {
    case e: Throwable =>
      scribe.error("Error in Consumer Fibre Should Be Handled Levels Above", e)
      IO.raiseError(e)
  }

  /**
   * This starts a seperate e-reader thread in the background. This thread is controlled by IB Framework SDK. Takes a whole Java thread. No
   * Fault Tolerance here in case is disconnects. THe Gateway will reconnect automatically though. Worry abuot it later. I guess this should
   * be a background thread that is cancellable to do the consumer loop. This is essentiall ?? Producers and this ONE consumer Fibre
   */
  protected def producerThread(eClientSocket: EClientSocket, eReader: EReader): IO[Unit] =
    scribe.info(s"Starting the e-reader producer (Java Thread) and consumer (in IO)")
    val checkPreconditions: IO[Unit] = IO.raiseUnless(eClientSocket.isConnected)(new Throwable("Not Connected before starting EReader"))
    val startThreader: IO[Unit]      = IO.delay(eReader.start()).void
    checkPreconditions *> startThreader

  def release(client: IBClient): IO[Unit]                        = IO(scribe.info(s"Should be releasing IBClient Resource"))
  def asResource(config: IBClientConfig): Resource[IO, IBClient] = Resource.make(setup(config))(release)
