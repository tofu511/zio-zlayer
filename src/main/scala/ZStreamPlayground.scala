import zio.console.putStrLn
import zio.stream.ZStream
import zio.{ExitCode, URIO, ZIO, console, random}

object Types {
  val oneValue: ZIO[Any, Nothing, Int] = ZIO.succeed(1)
  val oneFailure: ZIO[Any, Throwable, Int] = ZIO.fail(new RuntimeException)
  val requireRandom: ZIO[random.Random, Nothing, Int] = random.nextInt

  val threeValues: ZStream[Any, Nothing, Int] = ZStream(1,2,3)
  val empty: ZStream[Any, Nothing, Nothing] = ZStream.empty
  val valueThenFailure: ZStream[Any, Throwable, Int] =
    ZStream(1,2) ++ ZStream.fail(new RuntimeException)
}

object ZStreamPlayground extends zio.App {
  def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] =
    for {
      elements <- ZStream("Hello", "World").runCollect
      _ <- putStrLn(elements.toString)
    } yield ExitCode.success
}

object InfiniteStreams extends zio.App {
  def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] =
    ZStream.iterate(0)(_ + 1).take(20).runCollect.flatMap { chunk =>
      putStrLn(chunk.toString)
    }.exitCode
}

object Effects extends zio.App {
  def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] =
    (ZStream.fromEffect(putStrLn("Hello")).drain ++
      ZStream.iterate(0)(_ + 1)).tap(i => putStrLn((i * 2).toString)).take(20).runCollect.exitCode
}

object ControlFlow extends zio.App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    ZStream.repeatEffect(console.getStrLn).take(5).tap(line => putStrLn(line)).runCollect.exitCode
}

object Transforming extends zio.App {
  case class StockQuote(symbol: String, openPrice: Double, closePrice: Double)

  val streamStocks: ZStream[Any, Nothing, StockQuote] = ZStream(StockQuote("DOOG", 37.84, 39.00), StockQuote("NET", 18.40, 19.01))
  val streamSymbols: ZStream[Any, Nothing, String] = streamStocks.map(_.symbol)
  val streamOpenAndClose: ZStream[Any, Nothing, (String, Double)] = streamStocks.flatMap { case StockQuote(symbol, open, close) =>
    ZStream(
      symbol -> open,
      symbol -> close
    )
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    streamOpenAndClose.tap(s => putStrLn(s._1 + " " + s._2)).runCollect.exitCode
}