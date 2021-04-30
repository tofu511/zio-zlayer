import ZIOPlayground.UserDb.UserDbEnv
import ZIOPlayground.UserEmailer.UserEmailerEnv
import ZIOPlayground.UserSubscription.UserSubscriptionEnv
import zio.{Has, IO, Task, UIO, ZIO, ZLayer}
import zio.console._

import java.io.IOException

object ZIOPlayground extends zio.App {

  val success: UIO[Int] = ZIO.succeed(42)
  val fail: IO[String, Nothing] = ZIO.fail("something went wrong")

 val greeting = for {
   _ <- putStrLn("Hi, what's your name?")
   name <- getStrLn
   _ <- putStrLn(s"Hello, $name")
 } yield ()

  case class User(name: String, email: String)
  object UserEmailer {
    type UserEmailerEnv = Has[UserEmailer.Service]
    // service def
    trait Service {
      def notify(user: User, message: String): Task[Unit]
    }

    // service impl
    val live: ZLayer[Any, Nothing, UserEmailerEnv] = ZLayer.succeed(new Service {
      override def notify(user: User, message: String): Task[Unit] = Task {
        println(s"[User emailer] Sending $message to ${user.email}")
      }
    })

    // front-facing API
    def notify(user: User, message: String): ZIO[UserEmailerEnv, Throwable, Unit] = ZIO.accessM(_.get.notify(user, message))
  }

  object UserDb {
    type UserDbEnv = Has[UserDb.Service]
    trait Service {
      def insert(user: User): Task[Unit]
    }

    val live: ZLayer[Any, Nothing, UserDbEnv] = ZLayer.succeed(new Service {
      override def insert(user: User): Task[Unit] = Task {
        println(s"[Database] insert into public.user values ('${user.email}')")
      }
    })

    def insert(user: User): ZIO[UserDbEnv, Throwable, Unit] = ZIO.accessM(_.get.insert(user))
  }

  // Horizontal composition
  // Zlayer[In1, E1, Out1] ++ ZLayer[In2, E2, Out2] => Zlayer[In1 with In2, super(E1, E2), Out1 with Out2]

  val userBackendLayer: ZLayer[Any, Nothing, UserDbEnv with UserEmailerEnv] = UserDb.live ++ UserEmailer.live

  // Vertical composition
  object UserSubscription {
    type UserSubscriptionEnv = Has[UserSubscription.Service]
    class Service(notifier: UserEmailer.Service, userDb: UserDb.Service) {
      def subscribe(user: User): Task[User] =
        for {
          _ <- userDb.insert(user)
          _ <- notifier.notify(user, s"Welcome ${user.name}")
        } yield user
    }
    val live = ZLayer.fromServices[UserEmailer.Service, UserDb.Service, UserSubscription.Service] { (userEmaier, userDb) =>
      new Service(userEmaier, userDb)
    }

    def subscribe(user: User): ZIO[UserSubscriptionEnv, Throwable, User] = ZIO.accessM(_.get.subscribe(user))
  }

  val userSubscriptionLayer: ZLayer[Any, Nothing, UserSubscriptionEnv] = userBackendLayer >>> UserSubscription.live


  val tomo = User("Tomo", "tomo@examle.com")
  def notifyTomo() =
    UserEmailer.notify(tomo, "welcome!")
      .provideLayer(userBackendLayer)
      .exitCode

  override def run(args: List[String]) =
    UserSubscription.subscribe(tomo)
      .provideLayer(userSubscriptionLayer)
      .exitCode
}
