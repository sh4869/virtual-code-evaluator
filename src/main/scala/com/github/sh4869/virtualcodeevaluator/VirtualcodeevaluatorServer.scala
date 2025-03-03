package com.github.sh4869.virtualcodeevaluator

import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import cats.implicits._
import fs2.Stream
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import scala.concurrent.ExecutionContext.global
import scala.concurrent.ExecutionContext

object VirtualcodeevaluatorServer {

  def stream[F[_]: ConcurrentEffect](implicit
      T: Timer[F],
      C: ContextShift[F],
      ec: ExecutionContext
  ): Stream[F, Nothing] = {
    for {
      client <- BlazeClientBuilder[F](global).stream
      helloWorldAlg = HelloWorld.impl[F]
      jokeAlg = Jokes.impl[F](client)
      docker = Docker.impl[F]

      // Combine Service Routes into an HttpApp.
      // Can also be done via a Router if you
      // want to extract a segments not checked
      // in the underlying routes.
      httpApp = (
          VirtualcodeevaluatorRoutes.helloWorldRoutes[F](helloWorldAlg) <+>
            VirtualcodeevaluatorRoutes.jokeRoutes[F](jokeAlg) <+>
            VirtualcodeevaluatorRoutes.DockerRoutes[F](docker)
      ).orNotFound

      // With Middlewares in place
      finalHttpApp = Logger.httpApp(true, true)(httpApp)

      exitCode <- BlazeServerBuilder[F](global).bindHttp(8080, "0.0.0.0").withHttpApp(finalHttpApp).serve
    } yield exitCode
  }.drain
}
