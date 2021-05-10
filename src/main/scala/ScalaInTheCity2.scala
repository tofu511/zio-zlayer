import io.circe.generic.auto._
import sttp.client.UriContext
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient, send, sendR}
import sttp.client3.basicRequest
import sttp.client3.circe.asJson
import zio._
import zio.console._

object ScalaInTheCity2 extends App {

  final case class SearchResult(items: List[Repo])

  final case class Repo(full_name: String) {
    def owner: String =
      full_name.split('/')(0)
    def name: String =
      full_name.split('/')(1)
  }

  final case class Contributor(login: String, contributions: Int)

  type Github = Has[Github.Service]

  object Github {

    trait Service {
      def getRepos(language: String, limit: Int): ZIO[Github, Throwable, List[Repo]]
      def getContributors(repo: Repo): ZIO[Github, Throwable, List[Contributor]]
    }

    val live: ZLayer[SttpClient, Nothing, Github] = {
      (for {
        client <- ZIO.environment[SttpClient]
      } yield new Service {
          def getRepos(language: String, limit: Int): ZIO[Github, Throwable, List[Repo]] = {
            val request = basicRequest
              .get(
                uri"https://api.github.com/search/repositories?q=language:$language&sort=stars&per_page=$limit"
              )
              .response(asJson[SearchResult])
            sendR(request).map(_.body).absolve.map(_.items).provide(client)
          }
          def getContributors(repo: Repo): ZIO[Github, Throwable, List[Contributor]] = {
            val request = basicRequest
              .get(
                uri"https://api.github.com/repos/${repo.owner}/${repo.name}/contributors"
              )
              .response(asJson[List[Contributor]])
            sendR(request).map(_.body).absolve.provide(client)
          }
        }).toLayer
      }

    def getRepos(
                  language: String,
                  limit: Int
                ): ZIO[Github, Throwable, List[Repo]] =
      ZIO.accessM(_.get.getRepos(language, limit))

    def getContributors(repo: Repo): ZIO[Github, Throwable, List[Contributor]] =
      ZIO.accessM(_.get.getContributors(repo))

  }

  def notScalaSteward(contributors: List[Contributor]): List[Contributor] =
    contributors.filterNot(_.login == "scala-steward")

  def contributionsByUser(contributors: List[Contributor]): List[Contributor] =
    contributors
      .groupBy(_.login)
      .mapValues(_.map(_.contributions).sum)
      .map {
        case (login, contributions) => Contributor(login, contributions)
      }
      .toList
      .sortBy(_.contributions)
      .reverse

  def topContributors(
                       language: String,
                       limit: Int
                     ): ZIO[Github, Throwable, List[Contributor]] =
    Github
      .getRepos(language, limit)
      .flatMap(ZIO.foreachPar(_)(Github.getContributors))
      .map(_.flatten)
      .map(notScalaSteward)
      .map(contributionsByUser)

  val liveEnvironment: ZLayer[Any, Throwable, Github] =
    AsyncHttpClientZioBackend.layer() >>> Github.live

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    topContributors("scala", 25)
      .flatMap(result => putStrLn(result.mkString("\n")))
      .provideCustomLayer(liveEnvironment)
      .exitCode
}