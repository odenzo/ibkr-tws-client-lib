package com.odenzo.ibkr.tws

import cats.effect.*
import com.odenzo.ibkr.models.OPrint.oprint

import munit.*
import munit.CatsEffectAssertions.*
import org.http4s.client.Client

/** Integration tests which are ignored if `isCI` environment variable is defined. */
trait IntegrationTest extends BaseTest with TWSTestConfig {}
