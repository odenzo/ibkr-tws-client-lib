package com.odenzo.ibkr.gateway

import cats.effect.{Async, Concurrent, IO}
import cats.effect.std.{Dispatcher, Queue}
import cats.effect.syntax.{given, *}
import fs2.Stream
import fs2.concurrent.Topic

/**
 * FS2 v3 style component to enable Pub Sub stream with multiple subscribers. Use case is *Ticket taking callbacks from IBWrapper,
 * converting to T and allowing subscribers to access the Stream, rather than maintaining point-in-time state in the Ticket. Maybe useful?
 * Maybe a pain? This has to deal with not running on the callback thread of course, and enqueing quickly.
 */
class PubSubComponent[T](val dispatcher: Dispatcher[IO], val queue: Queue[IO, Option[T]], val topic: Topic[IO, T]) {

  /**
   * val writer: Stream[F. Unit] = topic.publish1(something) val reader: Stream[F, A] = topic.subscribe.something
   * reader.concurrently(writer)
   *
   * Try this and return the reader to the person who calls .submit()
   */
  // def enqueue(v: Option[T]): Unit = dispatcher.enqueueNoneTerminated(queue).unsafeRunAndForget(q.offer(v))
  // fs2.concurrent.Channel
}
object PubSubComponent:
  private def createDispatcher[T](): Stream[IO, Dispatcher[IO]]                                  = fs2.Stream.resource(Dispatcher[IO])
  private def createQueue[T](): Stream[IO, Queue[IO, Option[T]]]                                 = fs2.Stream.eval(Queue.unbounded[IO, Option[T]])
  private def createTopic[F[_], T]()(using F: cats.effect.Concurrent[F]): Stream[F, Topic[F, T]] =
    fs2.Stream.eval(fs2.concurrent.Topic.apply[F, T])

  private def createSharedTopic[F[_], T](topicId: String)(using F: cats.effect.Concurrent[F]) =
    Stream.eval(createTopic())

  def create[F[_], T](using F: cats.effect.Concurrent[F]) = {
    val dispatcher: Stream[IO, Dispatcher[IO]]  = createDispatcher()
    val queue: Stream[IO, Queue[IO, Option[T]]] = createQueue()
    val topic: Stream[F, Topic[F, Nothing]]     = createTopic()
    // queue.enqueueNoneTerminated(topic)
  }
