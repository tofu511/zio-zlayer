import zio._
import zio.test._
import zio.random.Random
import Assertion._
import zio.clock.Clock

object LayerTest extends DefaultRunnableSpec {

  val firstNames = List( "Ed", "Jane", "Joe", "Linda", "Sue", "Tim", "Tom")
  type Names = Has[Names.Service]
  type Teams = Has[Teams.Service]
  type History = Has[History.Service]
  type History2 = Has[History2.Service]

  object Names {
    trait Service {
      def randomName: UIO[String]
    }
    case class NamesImpl(random: Random.Service) extends Names.Service {
      override def randomName: UIO[String] = random.nextIntBounded(firstNames.size).map(firstNames)
    }
    val live: ZLayer[Random, Nothing, Names] = ZLayer.fromService(NamesImpl)
    def randomName: ZIO[Names, Nothing, String] = ZIO.accessM[Names](_.get.randomName)
  }


  object Teams {
    trait Service {
      def pickTeam(size: Int): UIO[Set[String]]
    }

    case class TeamsImpl(names: Names.Service) extends Service {
      override def pickTeam(size: Int): UIO[Set[String]] = ZIO.collectAll(0.until(size).map { _ => names.randomName }).map(_.toSet)
    }
    val live: ZLayer[Names, Nothing, Teams] = ZLayer.fromService(TeamsImpl)
    def pickTeam(size: Int): ZIO[Teams, Nothing, Set[String]] = ZIO.accessM[Teams](_.get.pickTeam(size))
  }

  object History {
    trait Service {
      def wonLastYear(team: Set[String]): Boolean
    }
    case class HistoryImpl(lastYearsWinners: Set[String]) extends Service {
      override def wonLastYear(team: Set[String]): Boolean = lastYearsWinners == team
    }
    val live: ZLayer[Teams, Nothing, History] = ZLayer.fromServiceM { teams: Teams.Service =>
      teams.pickTeam(5).map(nt => HistoryImpl(nt))
    }
    def wonLastYear(team: Set[String]) = ZIO.access[History](_.get.wonLastYear(team))
  }

  object History2 {
    trait Service {
      def wonLastYear(team: Set[String]): Boolean
    }
    case class History2Impl(lastYearsWinners: Set[String], lastYear: Long) extends Service {
      override def wonLastYear(team: Set[String]): Boolean = lastYearsWinners == team
    }
    val live: ZLayer[Clock with Teams, Nothing, History2] = ZLayer.fromEffect {
      for {
        someTime <- ZIO.accessM[Clock](_.get.nanoTime)
        team <- Teams.pickTeam(5)
      } yield History2Impl(team, someTime)
    }
    def wonLastYear(team: Set[String]) = ZIO.access[History2](_.get.wonLastYear(team))
  }

  def namesTest: ZSpec[Names, Nothing] = testM("names test") {
    for {
      name <- Names.randomName
    } yield {
      assert(firstNames.contains(name))(equalTo(true))
    }
  }

  def justTeamsTest: ZSpec[Teams, Nothing] = testM("small team test") {
    for {
      team <- Teams.pickTeam(1)
    } yield {
      assert(team.size)(equalTo(1))
    }
  }

  def inMyTeam: ZSpec[Teams with Names, Nothing] = testM("combines names and teams") {
    for {
      name <- Names.randomName
      team <- Teams.pickTeam(5)
      _ = if (team.contains(name)) println("one of mine")
      else println("not mine")
    } yield assertCompletes
  }

  def wonLastYear: ZSpec[History with Teams, Nothing] = testM("won last year") {
    for {
      team <- Teams.pickTeam(5)
      _ <- History.wonLastYear(team)
    } yield assertCompletes
  }

  def wonLastYear2: ZSpec[History2 with Teams, Nothing] = testM("won last year") {
    for {
      team <- Teams.pickTeam(5)
      _ <- History2.wonLastYear(team)
    } yield assertCompletes
  }

  val individually = suite("individually")(
    suite("needs Names")(
      namesTest
    ).provideCustomLayer(Names.live),
    suite("needs just Team")(
      justTeamsTest
    ).provideCustomLayer(Names.live >>> Teams.live),
    suite("needs Names and Teams")(
      inMyTeam
    ).provideCustomLayer(Names.live ++ (Names.live >>> Teams.live)),
    suite("needs History and Teams")(
      wonLastYear
    ).provideLayerShared((Names.live >>> Teams.live) ++ (Names.live >>> Teams.live >>> History.live)),
    suite("needs History2 and Teams")(
      wonLastYear2
    ).provideLayerShared((Names.live >>> Teams.live) ++ ((Names.live >>> Teams.live) ++ Clock.any >>> History2.live))
  )

  val altogether = suite("all together")(
    suite("needs Names")(
      namesTest
    ),
    suite("needs just Team")(
      justTeamsTest
    ),
    suite("needs Names and Teams")(
      inMyTeam
    ),
    suite("needs History and Teams")(wonLastYear)
  ).provideLayerShared(Names.live ++ (Names.live >>> Teams.live) ++ (Names.live >>> Teams.live >>> History.live))

  override def spec = individually
}
