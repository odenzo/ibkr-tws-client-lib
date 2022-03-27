package com.odenzo.ibkr.tws

import cats.Id
import cats.data.Chain
import cats.effect.{*, given}
import cats.effect.std.Dispatcher
import cats.effect.syntax.{*, given}
import com.ib.client.*
import com.odenzo.ibkr.tws.commands.*
import com.odenzo.ibkr.models.tws.*
import com.odenzo.ibkr.models.tws.SimpleTypes.*
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.{lang, util}

type UpdateConnectedFn = (connected: Boolean) => IO[Unit]

/**
 * For ReqId based callbacks this sends reponses to registered (id -> ticket) ticket. When no rqId, e.g. positions, a list of current
 * subscription is given. In future I may have one stream to subscribe also/instead.
 */
class IBWrapper(clientId: Int)(using val F: Async[IO]) extends EWrapper {

  private var connected: AtomicBoolean = new AtomicBoolean(false)

  /** Async setting when our connection state changes to connected */
  def setConnected(): Unit =
    scribe.warn(s"Connected To Server")
    connected.getAndSet(true) match {
      case true  => scribe.info("Double Connection")
      case false => scribe.info("New Connection...need to start reader?")
    }

  def setDisconnected(): Unit = connected.getAndSet(true) match {
    case true  => scribe.info("Initial Connection Closing...should close E-Reader")
    case false => scribe.info("Close for q Close...no action taken")
  }

  private val positionsHandlers               = scala.collection.mutable.Set.empty[PositionsTicket]
  private val accountUpdatesHandlers          = scala.collection.mutable.Set.empty[AccountUpdatesTicket]
  private val allIdActionRouter: RoutingTable = new RoutingTable(clientId)

  def addPositionsHandler(ticket: PositionsTicket): Unit    = positionsHandlers.addOne(ticket)
  def removePositionsHandler(ticket: PositionsTicket): Unit = positionsHandlers.remove(ticket)

  def addAccountUpdatesHandler(ticket: AccountUpdatesTicket): IO[Unit] = IO {
    accountUpdatesHandlers.headOption.foreach {
      et => IO.raiseUnless(et.rq.account == ticket.rq.account)(new Throwable(s"Account Mismatch $ticket"))
    }
    accountUpdatesHandlers.addOne(ticket)
  }.void

  def removeAccountUpdatesHandler(ticket: AccountUpdatesTicket): Unit =
    accountUpdatesHandlers.remove(ticket)

  def add(ticket: TicketWithId): Unit        = allIdActionRouter.add(ticket.rqId, ticket)
  def remove(id: RqId): Option[TicketWithId] = allIdActionRouter.remove(id)

  def connectAck(): Unit =
    scribe.info("Connection ACK")
    setConnected()

  def connectionClosed(): Unit =
    scribe.info("Connection Closed")
    setDisconnected()

  def error(e: Exception): Unit =
    scribe.error("General Exception", e)

  def error(str: String): Unit =
    scribe.error(s"General Exception: $str")

  def error(id: Int, errorCode: Int, errorMsg: String): Unit =
    if (id == -1) scribe.info(s"General Information: $id $errorCode $errorMsg")
    else scribe.error(s"General Exception: $id $errorCode $errorMsg")

  def route(rqId: Int, desc: String)(fn: TicketWithId => Unit): Unit                    = {
    scribe.info(s"Routing: $rqId $desc $fn")
    val id: RqId = RqId(rqId)
    this.allIdActionRouter.get(id) match {
      case None         => scribe.warn(s"$clientId:$rqId Not Ticket - $desc")
      case Some(ticket) => val ok: Unit =
          scribe.info(s"Calling Dispatch FN for $ticket")
          fn(ticket) // This is actually a partical function in a way. Need to deal with NotMatchException
      //      case Some(badTicket)            => scribe.warn(s"Ticket was not a TicketWithId, $badTicket")
    }
  }
  def contractDetails(reqId: Int, contractDetails: com.ib.client.ContractDetails): Unit = {
    scribe.warn(s"Contract Details: $reqId")
    val desc = s"Contract Details: ReqID: $reqId  $contractDetails"
    scribe.info(desc)
    route(reqId, desc) {
      case ticket: ContractQuoteTicket => ticket.contractDetails(contractDetails)
    }
  }

  def contractDetailsEnd(reqId: Int): Unit =
    scribe.warn(s"Contract Details End $reqId")
    val desc = s"Contract Details End ReqID: $reqId "
    route(reqId, desc) {
      case ticket: ContractQuoteTicket =>
        allIdActionRouter.remove(RqId(reqId))
        ticket.contractDetailsEnd()
    }

  def accountDownloadEnd(accountName: String): Unit =
    scribe.info(s"AccountDownloadEnd $accountName")

  def accountSummary(reqId: Int, account: String, tag: String, value: String, currency: String): Unit =
    route(reqId, s"AccountSumary $reqId $account $tag $value $currency") {
      case ticket: ContractQuoteTicket  => scribe.error(s"Wrong Ticket Type for accountSummary callback")
      case ticket: AccountSummaryTicket => ticket.accountSummary(IBAccount(account), IBTag(tag), value, currency)
    }

  /** In this case it marks that all requested tag information has been sent, but there is still an active subscription.(?) */
  def accountSummaryEnd(reqId: Int): Unit =
    scribe.info(s"AccountSummaryEnd $reqId")

  def accountUpdateMulti(reqId: Int, account: String, modelCode: String, key: String, value: String, currency: String): Unit =
    scribe.info(s"AccountUpdateMulti")

  def accountUpdateMultiEnd(reqId: Int): Unit = scribe.info(s"AccountUpdateMultiEnd: $reqId")

  def bondContractDetails(reqId: Int, contractDetails: com.ib.client.ContractDetails): Unit = scribe.info(s"bondContractDetails")

  def commissionReport(commissionReport: com.ib.client.CommissionReport): Unit = scribe.info(s"commissionReport")

  def completedOrder(contract: com.ib.client.Contract, order: com.ib.client.Order, orderState: com.ib.client.OrderState): Unit =
    scribe.info(s"AccountDownloadEnd")

  def completedOrdersEnd(): Unit =
    scribe.info(s"completedOrdersEnd")

  def currentTime(time: Long): Unit =
    scribe.info(s"Current Time $time")

  def deltaNeutralValidation(reqId: Int, deltaNeutralContract: com.ib.client.DeltaNeutralContract): Unit =
    scribe.info(s"AccountDownloadEnd")

  def displayGroupList(reqId: Int, groups: String): Unit = scribe.info(s"DisplayGroupList $reqId $groups")

  def displayGroupUpdated(reqId: Int, contractInfo: String): Unit =
    scribe.info(s"AccountDownloadEnd") // Not, we may need to wait for this before doing stuff, as part of init process

  def execDetails(reqId: Int, contract: com.ib.client.Contract, execution: com.ib.client.Execution): Unit =
    scribe.info(s"execDetails")

  def execDetailsEnd(reqId: Int): Unit = scribe.info(s"execDetailsEnd $reqId")

  def familyCodes(familyCodes: Array[com.ib.client.FamilyCode]): Unit = scribe.info(s"AccountDownloadEnd")

  def fundamentalData(reqId: Int, data: String): Unit = scribe.info(s"fundamentalData")

  def headTimestamp(reqId: Int, headTimestamp: String): Unit = scribe.info(s"headTimestamp")

  def histogramData(reqId: Int, items: java.util.List[com.ib.client.HistogramEntry]): Unit = scribe.info(s"histogramData")

  def historicalData(reqId: Int, bar: com.ib.client.Bar): Unit = scribe.info(s"AccountDownloadEnd")

  def historicalDataEnd(reqId: Int, startDateStr: String, endDateStr: String): Unit = scribe.info(s"AccountDownloadEnd")

  def historicalDataUpdate(reqId: Int, bar: com.ib.client.Bar): Unit = scribe.info(s"AccountDownloadEnd")

  def historicalNews(requestId: Int, time: String, providerCode: String, articleId: String, headline: String): Unit =
    scribe.info(s"AccountDownloadEnd")

  def historicalNewsEnd(requestId: Int, hasMore: Boolean): Unit = scribe.info(s"AccountDownloadEnd")

  def historicalSchedule(
      reqId: Int,
      startDateTime: String,
      endDateTime: String,
      timeZone: String,
      sessions: java.util.List[com.ib.client.HistoricalSession]
  ): Unit = scribe.info(s"AccountDownloadEnd")

  def historicalTicks(reqId: Int, ticks: java.util.List[com.ib.client.HistoricalTick], done: Boolean): Unit =
    scribe.info(s"AccountDownloadEnd")

  def historicalTicksBidAsk(reqId: Int, ticks: java.util.List[com.ib.client.HistoricalTickBidAsk], done: Boolean): Unit =
    scribe.info(s"AccountDownloadEnd")

  def historicalTicksLast(reqId: Int, ticks: java.util.List[com.ib.client.HistoricalTickLast], done: Boolean): Unit =
    scribe.info(s"AccountDownloadEnd")

  def managedAccounts(accountsList: String): Unit = scribe.info(s"Managed Accounts $accountsList")

  def marketDataType(reqId: Int, marketDataType: Int): Unit = scribe.info(s"marketDataType: $marketDataType $reqId ")

  def marketRule(marketRuleId: Int, priceIncrements: Array[com.ib.client.PriceIncrement]): Unit = scribe.info(s"AccountDownloadEnd")

  def mktDepthExchanges(depthMktDataDescriptions: Array[com.ib.client.DepthMktDataDescription]): Unit = scribe.info(s"AccountDownloadEnd")

  def newsArticle(requestId: Int, articleType: Int, articleText: String): Unit = scribe.info(s"AccountDownloadEnd")

  def newsProviders(newsProviders: Array[com.ib.client.NewsProvider]): Unit = scribe.info(s"AccountDownloadEnd")

  def nextValidId(orderId: Int): Unit =
    scribe.warn(s"nextValidId: $orderId")

  def openOrder(orderId: Int, contract: com.ib.client.Contract, order: com.ib.client.Order, orderState: com.ib.client.OrderState): Unit =
    scribe.info(s"RS: Open Order $orderId ${contract} $order $orderState")

  def openOrderEnd(): Unit = scribe.info(s"RS: OpenOrderEnd")

  def orderBound(orderId: Long, apiClientId: Int, apiOrderId: Int): Unit = scribe.info(s"AccountDownloadEnd")

  def orderStatus(
      orderId: Int,
      status: String,
      filled: com.ib.client.Decimal,
      remaining: com.ib.client.Decimal,
      avgFillPrice: Double,
      permId: Int,
      parentId: Int,
      lastFillPrice: Double,
      clientId: Int,
      whyHeld: String,
      mktCapPrice: Double
  ): Unit =
    scribe.info(
      s"""RS OrderStatus: OrderId $orderId Perm Id $permId Client Id $clientId $status $filled / $remaining
         | Ave Fill Price: $avgFillPrice
         | Market Cap: $mktCapPrice
         | Why Held? $whyHeld""".stripMargin
    )

  def pnl(reqId: Int, dailyPnL: Double, unrealizedPnL: Double, realizedPnL: Double): Unit =
    route(reqId, s"pnlAccount $reqId $dailyPnL $unrealizedPnL $realizedPnL ") {
      case ticket: AccountProfitAndLossTicket =>
        val wrapper = PnLAccount(BigDecimal(dailyPnL), BigDecimal(unrealizedPnL), BigDecimal(realizedPnL))
        ticket.pnl(wrapper)

    }

  def pnlSingle(reqId: Int, pos: com.ib.client.Decimal, dailyPnL: Double, unrealizedPnL: Double, realizedPnL: Double, value: Double): Unit =
    route(reqId, s"pnlSingle $reqId $pos $dailyPnL $value ") {
      case ticket: SingleProfitAndLossTicket =>
        val wrapper = PnLSingle(pos.value(), BigDecimal(dailyPnL), BigDecimal(unrealizedPnL), BigDecimal(realizedPnL), BigDecimal(value))
        ticket.pnlSingle(RqId(reqId), wrapper)

    }

  def position(account: String, contract: com.ib.client.Contract, pos: com.ib.client.Decimal, avgCost: Double): Unit =
    scribe.info(s"Position $account ${pprint(contract)} $pos $avgCost")
    positionsHandlers.foreach(ticket => ticket.position(IBAccount(account), contract, pos.value, avgCost))

  /** Dispatches to all registered position handlers and then clears registered handlers */
  def positionEnd(): Unit =
    scribe.info(s"AccountDownloadEnd")
    positionsHandlers.foreach(ticket => ticket.positionEnd())
    positionsHandlers.clear() // Not sure if end is current list, or subscription cancelled

  def positionMulti(
      reqId: Int,
      account: String,
      modelCode: String,
      contract: com.ib.client.Contract,
      pos: com.ib.client.Decimal,
      avgCost: Double
  ): Unit = scribe.info(s"AccountDownloadEnd")

  def positionMultiEnd(reqId: Int): Unit = scribe.info(s"AccountDownloadEnd")

  def realtimeBar(
      reqId: Int,
      time: Long,
      open: Double,
      high: Double,
      low: Double,
      close: Double,
      volume: com.ib.client.Decimal,
      wap: com.ib.client.Decimal,
      count: Int
  ): Unit = scribe.info(s"AccountDownloadEnd")

  def receiveFA(faDataType: Int, xml: String): Unit = scribe.info(s"AccountDownloadEnd")

  def replaceFAEnd(reqId: Int, text: String): Unit = scribe.info(s"AccountDownloadEnd")

  def rerouteMktDataReq(reqId: Int, conId: Int, exchange: String): Unit = scribe.info(s"AccountDownloadEnd")

  def rerouteMktDepthReq(reqId: Int, conId: Int, exchange: String): Unit = scribe.info(s"AccountDownloadEnd")

  def scannerData(
      reqId: Int,
      rank: Int,
      contractDetails: com.ib.client.ContractDetails,
      distance: String,
      benchmark: String,
      projection: String,
      legsStr: String
  ): Unit = scribe.info(s"AccountDownloadEnd")

  def scannerDataEnd(reqId: Int): Unit = scribe.info(s"AccountDownloadEnd")

  def scannerParameters(xml: String): Unit = scribe.info(s"AccountDownloadEnd")

  def securityDefinitionOptionalParameter(
      reqId: Int,
      exchange: String,
      underlyingConId: Int,
      tradingClass: String,
      multiplier: String,
      expirations: java.util.Set[java.lang.String],
      strikes: java.util.Set[java.lang.Double]
  ): Unit = scribe.info(s"AccountDownloadEnd")

  def securityDefinitionOptionalParameterEnd(reqId: Int): Unit = scribe.info(s"AccountDownloadEnd")

  def smartComponents(reqId: Int, theMap: java.util.Map[Integer, java.util.Map.Entry[String, Character]]): Unit =
    scribe.info(s"AccountDownloadEnd")

  def softDollarTiers(reqId: Int, tiers: Array[com.ib.client.SoftDollarTier]): Unit = scribe.info(s"AccountDownloadEnd")

  override def symbolSamples(reqId: Int, contractDescriptions: Array[com.ib.client.ContractDescription]): Unit =
    route(reqId, s"symbolSamples $reqId $contractDescriptions") {
      case ticket: MatchingSymbolsTicket =>
        val wrapper: Chain[ContractDescription] = Chain.fromSeq(contractDescriptions)
        ticket.contractDetails(wrapper)
    }

  override def tickByTickAllLast(
      reqId: Int,
      tickType: Int,
      time: Long,
      price: Double,
      size: com.ib.client.Decimal,
      tickAttribLast: com.ib.client.TickAttribLast,
      exchange: String,
      specialConditions: String
  ): Unit = scribe.info(s"AccountDownloadEnd")

  def tickByTickBidAsk(
      reqId: Int,
      time: Long,
      bidPrice: Double,
      askPrice: Double,
      bidSize: com.ib.client.Decimal,
      askSize: com.ib.client.Decimal,
      tickAttribBidAsk: com.ib.client.TickAttribBidAsk
  ): Unit = scribe.info(s"AccountDownloadEnd")

  def tickByTickMidPoint(reqId: Int, time: Long, midPoint: Double): Unit = scribe.info(s"AccountDownloadEnd")

  def tickEFP(
      tickerId: Int,
      tickType: Int,
      basisPoints: Double,
      formattedBasisPoints: String,
      impliedFuture: Double,
      holdDays: Int,
      futureLastTradeDate: String,
      dividendImpact: Double,
      dividendsToLastTradeDate: Double
  ): Unit = scribe.info(s"AccountDownloadEnd")

  def tickGeneric(tickerId: Int, tickType: Int, value: Double): Unit = scribe.info(s"AccountDownloadEnd")

  def tickNews(tickerId: Int, timeStamp: Long, providerCode: String, articleId: String, headline: String, extraData: String): Unit =
    scribe.info(s"AccountDownloadEnd")

  def tickOptionComputation(
      tickerId: Int,
      field: Int,
      tickAttrib: Int,
      impliedVol: Double,
      delta: Double,
      optPrice: Double,
      pvDividend: Double,
      gamma: Double,
      vega: Double,
      theta: Double,
      undPrice: Double
  ): Unit = scribe.info(s"AccountDownloadEnd")

  def tickPrice(tickerId: Int, field: Int, price: Double, attrib: com.ib.client.TickAttrib): Unit = scribe.info(s"AccountDownloadEnd")

  def tickReqParams(tickerId: Int, minTick: Double, bboExchange: String, snapshotPermissions: Int): Unit =
    scribe.info(s"AccountDownloadEnd")

  def tickSize(tickerId: Int, field: Int, size: com.ib.client.Decimal): Unit = scribe.info(s"AccountDownloadEnd")

  def tickSnapshotEnd(reqId: Int): Unit = scribe.info(s"AccountDownloadEnd")

  def tickString(tickerId: Int, tickType: Int, value: String): Unit = scribe.info(s"AccountDownloadEnd")

  def updateAccountTime(timeStamp: String): Unit =
    scribe.info(s"updateAccountTime Local Date HH:MM $timeStamp")
//    LocalTime.of()

  def updateAccountValue(key: String, value: String, currency: String, accountName: String): Unit =
    scribe.info(s"updateAccountValue $key -> $value $currency $accountName")

  def updateMktDepth(tickerId: Int, position: Int, operation: Int, side: Int, price: Double, size: com.ib.client.Decimal): Unit =
    scribe.info(s"AccountDownloadEnd")

  def updateMktDepthL2(
      tickerId: Int,
      position: Int,
      marketMaker: String,
      operation: Int,
      side: Int,
      price: Double,
      size: com.ib.client.Decimal,
      isSmartDepth: Boolean
  ): Unit = ()

  def updateNewsBulletin(msgId: Int, msgType: Int, message: String, origExchange: String): Unit = scribe.info(s"AccountDownloadEnd")

  /** AccountUpdates */
  def updatePortfolio(
      contract: com.ib.client.Contract,
      position: com.ib.client.Decimal,
      marketPrice: Double,
      marketValue: Double,
      averageCost: Double,
      unrealizedPNL: Double,
      realizedPNL: Double,
      accountName: String
  ): Unit = scribe.info(s"UpdatePortfolio $contract $position $marketPrice $marketValue ...")

  def verifyAndAuthCompleted(isSuccessful: Boolean, errorText: String): Unit = scribe.info(s"verifyAndAuthCompleted()")

  def verifyAndAuthMessageAPI(apiData: String, xyzChallenge: String): Unit = scribe.info(s"verifyAndAuthMessageAPI()")

  def verifyCompleted(isSuccessful: Boolean, errorText: String): Unit = scribe.info(s"verifyCompleted()")

  def verifyMessageAPI(apiData: String): Unit = scribe.info(s"verifyMessageAPI()")

  def wshEventData(reqId: Int, dataJson: String): Unit = scribe.info(s"wshEventData()")

  def wshMetaData(reqId: Int, dataJson: String): Unit = scribe.info(s"wshMetaData()")

}
