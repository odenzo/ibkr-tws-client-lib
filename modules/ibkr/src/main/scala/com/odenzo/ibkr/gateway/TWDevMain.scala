package com.odenzo.ibkr.gateway

import cats.effect.{*, given}
import cats.effect.implicits.{*, given}
import cats.effect.kernel.implicits.{*, given}
import cats.*
import cats.data.*
import cats.implicits.*
import com.ib.controller.AccountSummaryTag
import com.odenzo.ibkr.gateway.commands.*
import com.odenzo.ibkr.gateway.models.{ConnectionInfo, IBClientConfig, IBContract}

import scala.concurrent.duration.*
import java.util.Currency
import com.ib.controller.AccountSummaryTag.*
import com.odenzo.ibkr.gateway.models.SimpleTypes.IBAccount
import scribe.Scribe
import _root_.scribe.cats.{io => scribeIO}

/** A crude test app / example */
object TWDevMain extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {

    scribe.info(s"Main Running..: $args")
    val config = IBClientConfig(13, IBAccount("DU1735480"), ConnectionInfo("127.0.0.1", 4002, 6))

    val prog = for {
      _        <- scribeIO.info("Main Running -- Doing Setup")
      _         = scribe.info("Not suspended")
      client   <- IBClient.setup(config)
      exitCode <- businessMain(config, client)
    } yield exitCode

    scribe.info("Scribe eager, main Prog construction done")
    prog >> scribeIO.info("Scribe IO - Prog Done") >> IO.pure(ExitCode.Success)
  }

  def businessMain(config: IBClientConfig, ibclient: IBClient): IO[ExitCode] = {
    given IBClient = ibclient
    val account    = config.account
    for {
      _                <- scribeIO.info("Starting Business Logic")
      accountSumTicket <- AccountSummaryRq("All", AccountSummaryTag.values().mkString(",")).submit()
      positionTicker   <- PositionsRq().submit()
      acctUpdatesTicket <- AccountUpdatesRq(true, account).submit() // Must be true to get any data, false to stop
      _                <- AccountProfitAndLossRq(config.account, None).submit()
      _                <- AccountProfitAndLossRq(account, None).submit()
      stockTicket      <- ContractQuoteRq(IBContract("NVDA", "STK", Currency.getInstance("USD"), "ISLAND").toWire).submit()
      _                <- IO.sleep(10.minute)

      // Don't bother cancelling now since close will shut down client connection
      status = ExitCode.Success
    } yield status
  }
}
