package com.odenzo.ibkr.tws

import cats.effect.IO
import com.odenzo.ibkr.models.OPrint.oprint
import munit.*
import munit.CatsEffectAssertions.*

final val include = new munit.Tag("include")
final val exclude = new munit.Tag("exclude")

/** Base testing trait that includes Cats Support and Assertions */
trait BaseTest extends CatsEffectSuite {

  val infoIO = (a: Any => IO[Unit]) => IO(scribe.info(s"${oprint(a)}"))
}
