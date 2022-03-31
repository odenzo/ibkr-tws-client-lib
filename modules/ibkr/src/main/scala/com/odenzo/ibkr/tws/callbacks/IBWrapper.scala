package com.odenzo.ibkr.tws.callbacks

import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.std.Dispatcher
import cats.effect.syntax.*
import cats.syntax.all.*
import com.ib.client.*
import com.odenzo.ibkr.models.tws.*
import com.odenzo.ibkr.models.tws.SimpleTypes.*
import com.odenzo.ibkr.tws.commands.*
import com.odenzo.ibkr.tws.commands.hybrid.*
import com.odenzo.ibkr.tws.commands.single.*
import com.odenzo.ibkr.tws.commands.special.PositionsTicket
import com.odenzo.ibkr.tws.commands.subscription.*
import com.odenzo.ibkr.tws.kernel.contractToIBContract
import com.odenzo.ibkr.tws.models.*

import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.{lang, util}
import scala.collection.concurrent.TrieMap

/**
 * Stateful extension of the EWrapper callback. For ReqId based callbacks this sends reponses to registered (id -> ticket) ticket. When no
 * rqId, e.g. positions, a list of current subscription is given. In future I may have one stream to subscribe also/instead.
 */
class IBWrapper(clientId: Int) extends EWrapper with IBWrapperAccountUpdates with IBWrapperPositionUpdates {
  import cats.effect.unsafe.implicits.global
  private var connected: AtomicBoolean = new AtomicBoolean(false) // Ref?

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

  /**
   * Everybody that asks for a position will get ALL the positions. Perhap a Topic here is better and then people can apply filter.
   * Currently the responses wont have a request id or handle to who requested.
   */

  // No concurrent Set or lisst
  private val positionsHandlers               = scala.collection.concurrent.TrieMap.empty[PositionsTicket, PositionsTicket]
  private val allIdActionRouter: RoutingTable = new RoutingTable(clientId)

  def add(ticket: TicketWithId): IO[Unit] = allIdActionRouter.add(ticket.rqId, ticket)
  def remove(id: RqId): IO[Unit]          = allIdActionRouter.remove(id)

  override def connectAck(): Unit =
    scribe.info("Connection ACK")
    setConnected()

  override def connectionClosed(): Unit =
    scribe.info("Connection Closed")
    setDisconnected()

  override def error(e: Exception): Unit =
    scribe.error("General Exception", e)

  override def error(str: String): Unit =
    scribe.error(s"General Exception: $str")

  override def error(id: Int, errorCode: Int, errorMsg: String): Unit =
    if (id == -1) scribe.info(s"General Information: $id $errorCode $errorMsg")
    else scribe.error(s"General Exception: $id $errorCode $errorMsg")

  def route(rqId: Int, desc: String)(fn: TicketWithId => IO[Unit]): Unit = {
    scribe.info(s"Routing: $rqId $desc $fn")
    val id: RqId = RqId(rqId)
    this.allIdActionRouter.get(id) match {
      case None         => scribe.warn(s"$clientId:$rqId Not Ticket - $desc")
      case Some(ticket) =>
        Dispatcher[IO].use {
          dispatcher =>
            IO {
              scribe.info(s"Calling Dispatch FN for $ticket")
              dispatcher.unsafeRunAndForget(fn(ticket))
            }
          // NotMatchException
          //      case Some(badTicket)            => scribe.warn(s"Ticket was not a TicketWithId, $badTicket")
        }
    }
  }

  override def accountSummary(reqId: Int, account: String, tag: String, value: String, currency: String): Unit =
    scribe.info(s"AccountSummary $reqId")
    route(reqId, s"AccountSumary $reqId $account $tag $value $currency") {
      case ticket: ContractDetailsTicket => IO(scribe.error(s"Wrong Ticket Type for accountSummary callback $ticket"))
      case ticket: AccountSummaryTicket  => ticket.accountSummary(IBAccount(account), IBTag(tag), value, currency).void
    }

  override def contractDetails(reqId: Int, contractDetails: com.ib.client.ContractDetails): Unit = {
    scribe.warn(s"Contract Details: $reqId")
    val desc = s"Contract Details: ReqID: $reqId  $contractDetails"
    scribe.info(desc)
    route(reqId, desc) {
      case ticket: ContractDetailsTicket => ticket.contractDetails(contractDetails).void
    }
  }

  override def contractDetailsEnd(reqId: Int): Unit =
    scribe.warn(s"Contract Details End $reqId")
    val desc = s"Contract Details End ReqID: $reqId "
    route(reqId, desc) {
      case ticket: ContractDetailsTicket =>
        allIdActionRouter.remove(RqId(reqId)) *> ticket.contractDetailsEnd().void
    }

  override def accountUpdateMulti(reqId: Int, account: String, modelCode: String, key: String, value: String, currency: String): Unit =
    scribe.info(s"AccountUpdateMulti $reqId")

  override def accountUpdateMultiEnd(reqId: Int): Unit = scribe.info(s"AccountUpdateMultiEnd: $reqId")

  override def bondContractDetails(reqId: Int, contractDetails: com.ib.client.ContractDetails): Unit = scribe.info(s"bondContractDetails")

  override def commissionReport(commissionReport: com.ib.client.CommissionReport): Unit = scribe.info(s"commissionReport")

  override def completedOrder(contract: com.ib.client.Contract, order: com.ib.client.Order, orderState: com.ib.client.OrderState): Unit =
    scribe.info(s"completedOrder")

  override def completedOrdersEnd(): Unit =
    scribe.info(s"completedOrdersEnd")

  override def currentTime(time: Long): Unit =
    scribe.info(s"Current Time $time")

  override def deltaNeutralValidation(reqId: Int, deltaNeutralContract: com.ib.client.DeltaNeutralContract): Unit =
    scribe.info(s"deltaNeutralValidation")

  override def displayGroupList(reqId: Int, groups: String): Unit = scribe.info(s"DisplayGroupList $reqId $groups")

  override def displayGroupUpdated(reqId: Int, contractInfo: String): Unit =
    scribe.info(s"displayGroupUpdated") // Not, we may need to wait for this before doing stuff, as part of init process

  override def execDetails(reqId: Int, contract: com.ib.client.Contract, execution: com.ib.client.Execution): Unit =
    scribe.info(s"execDetails $reqId")

  override def execDetailsEnd(reqId: Int): Unit = scribe.info(s"execDetailsEnd $reqId")

  override def familyCodes(familyCodes: Array[com.ib.client.FamilyCode]): Unit = scribe.info(s"familyCodes")

  override def fundamentalData(reqId: Int, data: String): Unit = scribe.info(s"fundamentalData $reqId")

  override def headTimestamp(reqId: Int, headTimestamp: String): Unit = scribe.info(s"headTimestamp $reqId")

  override def histogramData(reqId: Int, items: java.util.List[com.ib.client.HistogramEntry]): Unit = scribe.info(s"histogramData")

  override def historicalData(reqId: Int, bar: com.ib.client.Bar): Unit = scribe.info(s"historicalData")

  override def historicalDataEnd(reqId: Int, startDateStr: String, endDateStr: String): Unit = scribe.info(s"historicalDataEnd")

  override def historicalDataUpdate(reqId: Int, bar: com.ib.client.Bar): Unit = scribe.info(s"historicalDataUpdate")

  override def historicalNews(requestId: Int, time: String, providerCode: String, articleId: String, headline: String): Unit =
    scribe.info(s"historicalNews")

  override def historicalNewsEnd(requestId: Int, hasMore: Boolean): Unit = scribe.info(s"historicalNewsEnd")

  override def historicalSchedule(
      reqId: Int,
      startDateTime: String,
      endDateTime: String,
      timeZone: String,
      sessions: java.util.List[com.ib.client.HistoricalSession]
  ): Unit = scribe.info(s"historicalSchedule")

  override def historicalTicks(reqId: Int, ticks: java.util.List[com.ib.client.HistoricalTick], done: Boolean): Unit =
    scribe.info(s"historicalTicks")

  override def historicalTicksBidAsk(reqId: Int, ticks: java.util.List[com.ib.client.HistoricalTickBidAsk], done: Boolean): Unit =
    scribe.info(s"historicalTicksBidAsk")

  override def historicalTicksLast(reqId: Int, ticks: java.util.List[com.ib.client.HistoricalTickLast], done: Boolean): Unit =
    scribe.info(s"historicalTicksLast")

  override def managedAccounts(accountsList: String): Unit = scribe.info(s"Managed Accounts $accountsList")

  override def marketDataType(reqId: Int, marketDataType: Int): Unit = scribe.info(s"marketDataType: $marketDataType $reqId ")

  override def marketRule(marketRuleId: Int, priceIncrements: Array[com.ib.client.PriceIncrement]): Unit = scribe.info(s"marketRule")

  override def mktDepthExchanges(depthMktDataDescriptions: Array[com.ib.client.DepthMktDataDescription]): Unit =
    scribe.info(s"mktDepthExchanges")

  override def newsArticle(requestId: Int, articleType: Int, articleText: String): Unit = scribe.info(s"newsArticle")

  override def newsProviders(newsProviders: Array[com.ib.client.NewsProvider]): Unit = scribe.info(s"newsProviders")

  override def nextValidId(orderId: Int): Unit =
    scribe.warn(s"nextValidId: $orderId")

  override def openOrder(
      orderId: Int,
      contract: com.ib.client.Contract,
      order: com.ib.client.Order,
      orderState: com.ib.client.OrderState
  ): Unit =
    scribe.info(s"RS: Open Order $orderId ${contract} $order $orderState")

  override def openOrderEnd(): Unit = scribe.info(s"RS: OpenOrderEnd")

  override def orderBound(orderId: Long, apiClientId: Int, apiOrderId: Int): Unit = scribe.info(s"orderBound $orderId")

  override def orderStatus(
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

  override def pnl(reqId: Int, dailyPnL: Double, unrealizedPnL: Double, realizedPnL: Double): Unit =
    route(reqId, s"pnlAccount $reqId $dailyPnL $unrealizedPnL $realizedPnL ") {
      case ticket: AccountProfitAndLossTicket =>
        val wrapper = PnLAccount(BigDecimal(dailyPnL), BigDecimal(unrealizedPnL), BigDecimal(realizedPnL))
        ticket.pnl(wrapper).void

    }

  override def pnlSingle(
      reqId: Int,
      pos: com.ib.client.Decimal,
      dailyPnL: Double,
      unrealizedPnL: Double,
      realizedPnL: Double,
      value: Double
  ): Unit =
    route(reqId, s"pnlSingle $reqId $pos $dailyPnL $value ") {
      case ticket: SingleProfitAndLossTicket =>
        val wrapper = PnLSingle(pos.value(), BigDecimal(dailyPnL), BigDecimal(unrealizedPnL), BigDecimal(realizedPnL), BigDecimal(value))
        ticket.pnlSingle(RqId(reqId), wrapper).void

    }

  override def positionMulti(
      reqId: Int,
      account: String,
      modelCode: String,
      contract: com.ib.client.Contract,
      pos: com.ib.client.Decimal,
      avgCost: Double
  ): Unit = scribe.info(s"positionMulti $reqId")

  override def positionMultiEnd(reqId: Int): Unit = scribe.info(s"positionMultiEnd $reqId")

  override def realtimeBar(
      reqId: Int,
      time: Long,
      open: Double,
      high: Double,
      low: Double,
      close: Double,
      volume: com.ib.client.Decimal,
      wap: com.ib.client.Decimal,
      count: Int
  ): Unit = scribe.info(s"realtimeBar $reqId")

  override def receiveFA(faDataType: Int, xml: String): Unit = scribe.info(s"receiveFA")

  override def replaceFAEnd(reqId: Int, text: String): Unit = scribe.info(s"replaceFAEnd")

  override def rerouteMktDataReq(reqId: Int, conId: Int, exchange: String): Unit = scribe.info(s"rerouteMktDataReq")

  override def rerouteMktDepthReq(reqId: Int, conId: Int, exchange: String): Unit = scribe.info(s"rerouteMktDepthReq")

  override def scannerData(
      reqId: Int,
      rank: Int,
      contractDetails: com.ib.client.ContractDetails,
      distance: String,
      benchmark: String,
      projection: String,
      legsStr: String
  ): Unit = scribe.info(s"scannerData")

  override def scannerDataEnd(reqId: Int): Unit = scribe.info(s"scannerDataEnd")

  override def scannerParameters(xml: String): Unit = scribe.info(s"scannerParameters")

  override def securityDefinitionOptionalParameter(
      reqId: Int,
      exchange: String,
      underlyingConId: Int,
      tradingClass: String,
      multiplier: String,
      expirations: java.util.Set[java.lang.String],
      strikes: java.util.Set[java.lang.Double]
  ): Unit = scribe.info(s"securityDefinitionOptionalParameter")

  override def securityDefinitionOptionalParameterEnd(reqId: Int): Unit = scribe.info(s"securityDefinitionOptionalParameterEnd")

  override def smartComponents(reqId: Int, theMap: java.util.Map[Integer, java.util.Map.Entry[String, Character]]): Unit =
    scribe.info(s"smartComponents")

  override def softDollarTiers(reqId: Int, tiers: Array[com.ib.client.SoftDollarTier]): Unit = scribe.info(s"softDollarTiers")

  override def symbolSamples(reqId: Int, contractDescriptions: Array[com.ib.client.ContractDescription]): Unit =
    scribe.info(s"Symbol Samples $reqId")
    route(reqId, s"symbolSamples $reqId ${contractDescriptions.mkString("Array(", ", ", ")")}") {
      case ticket: MatchingSymbolsTicket =>
        val wrapper: Chain[ContractDescription] = Chain.fromSeq(contractDescriptions)
        ticket.contractDetails(wrapper).void
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
  ): Unit = scribe.info(s"tickByTickAllLast")

  override def tickByTickBidAsk(
      reqId: Int,
      time: Long,
      bidPrice: Double,
      askPrice: Double,
      bidSize: com.ib.client.Decimal,
      askSize: com.ib.client.Decimal,
      tickAttribBidAsk: com.ib.client.TickAttribBidAsk
  ): Unit = scribe.info(s"tickByTickBidAsk")

  override def tickByTickMidPoint(reqId: Int, time: Long, midPoint: Double): Unit = scribe.info(s"tickByTickMidPoint")

  override def tickEFP(
      tickerId: Int,
      tickType: Int,
      basisPoints: Double,
      formattedBasisPoints: String,
      impliedFuture: Double,
      holdDays: Int,
      futureLastTradeDate: String,
      dividendImpact: Double,
      dividendsToLastTradeDate: Double
  ): Unit = scribe.info(s"tickEFP")

  override def tickGeneric(tickerId: Int, tickType: Int, value: Double): Unit = scribe.info(s"tickGeneric")

  override def tickNews(
      tickerId: Int,
      timeStamp: Long,
      providerCode: String,
      articleId: String,
      headline: String,
      extraData: String
  ): Unit =
    scribe.info(s"tickNews")

  override def tickOptionComputation(
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
  ): Unit = scribe.info(s"tickOptionComputation")

  override def tickPrice(tickerId: Int, field: Int, price: Double, attrib: com.ib.client.TickAttrib): Unit = scribe.info(s"tickPrice")

  override def tickReqParams(tickerId: Int, minTick: Double, bboExchange: String, snapshotPermissions: Int): Unit =
    scribe.info(s"tickReqParams")

  override def tickSize(tickerId: Int, field: Int, size: com.ib.client.Decimal): Unit = scribe.info(s"tickSize")

  override def tickSnapshotEnd(reqId: Int): Unit = scribe.info(s"tickSnapshotEnd")

  override def tickString(tickerId: Int, tickType: Int, value: String): Unit = scribe.info(s"tickString")

  override def updateMktDepth(tickerId: Int, position: Int, operation: Int, side: Int, price: Double, size: com.ib.client.Decimal): Unit =
    scribe.info(s"updateMktDepth")

  override def updateMktDepthL2(
      tickerId: Int,
      position: Int,
      marketMaker: String,
      operation: Int,
      side: Int,
      price: Double,
      size: com.ib.client.Decimal,
      isSmartDepth: Boolean
  ): Unit = ()

  override def updateNewsBulletin(msgId: Int, msgType: Int, message: String, origExchange: String): Unit =
    scribe.info(s"updateNewsBulletin")

  override def verifyAndAuthCompleted(isSuccessful: Boolean, errorText: String): Unit = scribe.info(s"verifyAndAuthCompleted()")

  override def verifyAndAuthMessageAPI(apiData: String, xyzChallenge: String): Unit = scribe.info(s"verifyAndAuthMessageAPI()")

  override def verifyCompleted(isSuccessful: Boolean, errorText: String): Unit = scribe.info(s"verifyCompleted()")

  override def verifyMessageAPI(apiData: String): Unit = scribe.info(s"verifyMessageAPI()")

  override def wshEventData(reqId: Int, dataJson: String): Unit = scribe.info(s"wshEventData()")

  override def wshMetaData(reqId: Int, dataJson: String): Unit = scribe.info(s"wshMetaData()")

}
