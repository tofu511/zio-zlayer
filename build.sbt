name := "zio-zlayer"

version := "0.1"

scalaVersion := "2.13.5"

Global / onChangedBuildSource := ReloadOnSourceChanges

val zioVersion = "1.0.7"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-streams" % zioVersion,
  "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "2.1.4",
  "com.softwaremill.sttp.client" %% "circe" % "2.1.4",
  "io.circe" %% "circe-generic" % "0.12.3",
  "dev.zio" %% "zio-test"          % zioVersion % "test",
  "dev.zio" %% "zio-test-sbt"      % zioVersion % "test",
  "dev.zio" %% "zio-test-magnolia" % zioVersion % "test",
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.3.0",
  "com.softwaremill.sttp.client3" %% "circe" % "3.3.0"
)
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
