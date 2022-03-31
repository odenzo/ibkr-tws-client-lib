package com.odenzo.ibkr.tws

import com.odenzo.ibkr.models.tws.SimpleTypes.IBAccount

/** Orthogonal to what tets framework */
trait TWSTestConfig {

  val connection: ConnectionInfo = ConnectionInfo("127.0.0.1", 4001, 666)
  val config: IBClientConfig     = IBClientConfig(67, IBAccount("DU1735480"), connection)
  val CI: Boolean                = TestConfig.inCI
}

object TestConfig:
  val inCI: Boolean = {
    val envCI  = scala.sys.env.get("CI").exists(_.equalsIgnoreCase("true"))
    val propCI = scala.sys.props.get("CI").exists(_.equalsIgnoreCase("true"))
    val CI     = envCI || propCI
    scribe.warn(s"****** Am IN CI: $CI  ($envCI , $propCI)")
    scribe.warn(s"****** Overriding CI to false")
    false
  }
