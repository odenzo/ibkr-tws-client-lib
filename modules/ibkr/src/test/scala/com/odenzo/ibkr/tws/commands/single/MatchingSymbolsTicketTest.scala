package com.odenzo.ibkr.tws.commands.single

import cats.data.Chain
import cats.effect.{IO, Resource}
import com.ib.client.ContractDescription
import com.odenzo.ibkr.tws.WeaverTestSupport
import com.odenzo.ibkr.tws.*
import weaver.IOSuite
import scala.concurrent.duration.*
import com.odenzo.ibkr.models.OPrint.oprint
object MatchingSymbolsTicketTest extends IOSuite with WeaverTestSupport {

  type Res = IBClient
  def sharedResource: Resource[IO, Res] = clientR

  test("oneSymbol") {
    (c: IBClient, log) =>
      given IBClient = c
      scribe.info("Running OneSymbol")
      IO(scribe.info("IOExec oneymbol")) *>
        MatchingSymbolsRq("NVD").submit().flatMap(_.waitForResults()).map {
          (data: Chain[ContractDescription]) =>
            scribe.info(s"Data ${oprint(data)}")
            log.info(s"Data: ${oprint(data)}")
            expect(data.isEmpty)
        }

  }

}
