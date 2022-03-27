package com.odenzo.ibkr.tws

import cats.effect.*
import cats.effect.std.*
import cats.effect.syntax.all.{*, given}
import cats.*
import cats.data.*
import cats.implicits.*
import fs2.{INothing, Pure, Stream}
import munit.FunSuite

import java.time.Instant
import scala.concurrent.duration.{*, given}
class PubSubComponentTest extends munit.CatsEffectSuite {

  test("Queue") {

    for {
      queue  <- std.Queue.unbounded[IO, Int]
      _      <- (0 to 100).toList.traverse(i => queue.offer(i))
      cnt    <- queue.size
      _       = scribe.info(s"Count: $cnt")
      first  <- queue.take
      second <- queue.tryTake
      _       = scribe.info(s"$first / $second")
      left   <- queue.size
      _       = scribe.info(s"Remaining $left")
      _       = assert(cnt > left)
    } yield (cnt, left)

  }

  test("Inifinite Ticker Based Stream to Queue") {
    import cats.effect.IO.asyncForIO

    val queue: IO[Queue[IO, Instant]] = std.Queue.unbounded[IO, Instant]
    val prog: IO[Unit]                = queue.map {
      (q: Queue[IO, Instant]) =>
        def update(t: Instant): Unit = {
          scribe.info(s"Got a Fake EWrapper Callback: Existing: ")
          q.offer(t)
        }

        val stream: Stream[IO, Instant] = fs2.Stream.fixedRate(5.seconds)
          .evalMap(_ => IO(Instant.now))
          .debug()
          .evalTap(i => IO.pure(update(i)))
        stream.compile.drain
    }.void
    (queue.start *> prog).unsafeRunSync()
  }
}
