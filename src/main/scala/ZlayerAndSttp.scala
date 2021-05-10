import zio._
import sttp.client3._
import sttp.client3.circe._
import sttp.client3.asynchttpclient.zio._
import io.circe.generic.auto._
import zio.console.Console


object ZlayerAndSttp extends App {

  case class HttpBinResponse(origin: String, headers: Map[String, String])

  type HttpBin = Has[HttpBin.Service]
  object HttpBin {
    trait Service {
      def sendRequest: ZIO[HttpBin, Throwable, HttpBinResponse]
    }

    val live: ZLayer[SttpClient, Nothing, HttpBin] =
      (for  {
        client <- ZIO.environment[SttpClient]
      } yield new Service {
        override def sendRequest: ZIO[HttpBin, Throwable, HttpBinResponse] = {
          val request = basicRequest
            .get(uri"https://httpbin.org/get")
            .response(asJson[HttpBinResponse])

          sendR(request)
            .map(_.body)
            .absolve
            .map(res => HttpBinResponse(res.origin, res.headers))
            .provide(client)
        }
      }).toLayer

    def sendRequest: ZIO[HttpBin, Throwable, HttpBinResponse] = ZIO.accessM(_.get.sendRequest)
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val program = for {
      result <- HttpBin.sendRequest
      _ <- console.putStrLn(s"${result.origin}, ${result.headers}")
    } yield ()
    program.provideCustomLayer(AsyncHttpClientZioBackend.layer() >>> HttpBin.live)
      .exitCode
  }

}
