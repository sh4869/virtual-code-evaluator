package com.github.sh4869.virtualcodeevaluator

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._

object Main extends IOApp {
  implicit val ec = scala.concurrent.ExecutionContext.global
  def run(args: List[String]) =
    VirtualcodeevaluatorServer.stream[IO].compile.drain.as(ExitCode.Success)
}
