package com.github.sh4869.virtualcodeevaluator

import java.io.File
import java.net.InetSocketAddress
import cats.effect.Async
import cats.implicits._
import java.nio.file.FileSystems
import akka.http.scaladsl.unmarshalling.Unmarshal
import scala.concurrent.Future
import akka.stream.alpakka.unixdomainsocket.scaladsl.UnixDomainSocket
import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.http.scaladsl._
import akka.actor.ClassicActorSystemProvider
import akka.util.ByteString
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import scala.concurrent.ExecutionContext
import cats.effect.ConcurrentEffect
import scala.util.Failure
import scala.util.Success

// copy from https://github.com/akka/akka-http/issues/2139#issuecomment-413535497
object DockerSockTransport extends ClientTransport {
  lazy val path: java.nio.file.Path =
    FileSystems.getDefault().getPath("/var/run/docker.sock")
  override def connectTo(host: String, port: Int, settings: ClientConnectionSettings)(implicit
      system: ActorSystem
  ): Flow[ByteString, ByteString, Future[Http.OutgoingConnection]] = {
    // ignore everything for now
    UnixDomainSocket().outgoingConnection(path).mapMaterializedValue { _ =>
      // Seems that the UnixDomainSocket.OutgoingConnection is never completed? It works anyway if we just assume it is completed
      // instantly
      Future.successful(
        Http.OutgoingConnection(
          InetSocketAddress.createUnresolved(host, port),
          InetSocketAddress.createUnresolved(host, port)
        )
      )
    }
  }
}

trait Docker[F[_]] {
  def ping: F[String]
  // TODO: 型付け
  def images: F[String]
}

object Docker {
  implicit def apply[F[_]](implicit ev: Docker[F]): Docker[F] = ev

  implicit val system: ActorSystem = ActorSystem()

  def impl[F[_]](implicit ec: ExecutionContext, f: Async[F]): Docker[F] =
    new Docker[F] {
      val settings = ConnectionPoolSettings(system).withTransport(DockerSockTransport)
      def handleHttp(res: HttpResponse) = Unmarshal(res).to[String]

      private def request(path: String) = {
        f.async[String] { cb: (Either[Throwable, String] => Unit) =>
          Http()
            .singleRequest(HttpRequest(uri = s"http://localhost/${path}"), settings = settings)
            .flatMap(handleHttp)
            .onComplete {
              case Success(v)         => cb(Right(v))
              case Failure(exception) => cb(Left(exception))
            }
        }
      }

      def ping: F[String] = request("_ping")
      def images: F[String] = request("images/json")
    }
}
