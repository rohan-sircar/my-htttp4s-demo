val Http4sVersion = "0.21.16"
val CirceVersion = "0.13.0"
val MunitVersion = "0.7.20"
val LogbackVersion = "1.2.3"
val MunitCatsEffectVersion = "0.13.0"
val FlywayVersion = "7.5.3"
scalaVersion in ThisBuild := "2.13.4"

resolvers += "jitpack" at "https://jitpack.io"

import com.github.tototoshi.sbt.slick.CodegenPlugin.autoImport.{
  slickCodegenDatabasePassword,
  slickCodegenDatabaseUrl,
  slickCodegenJdbcDriver
}

import _root_.slick.codegen.SourceCodeGenerator
import _root_.slick.{model => m}

lazy val codegenDbHost = sys.env.getOrElse("CODEGEN_DB_HOST", "localhost")
lazy val codegenDbPort = sys.env.getOrElse("CODEGEN_DB_PORT", "5432")
lazy val codegenDbName = sys.env.getOrElse("CODEGEN_DB_NAME", "test_db")

lazy val databaseUrl =
  s"jdbc:postgresql://$codegenDbHost:$codegenDbPort/$codegenDbName"

lazy val databaseUser = sys.env.getOrElse("CODEGEN_DB_USER", "test_user")
lazy val databasePassword = sys.env.getOrElse("CODEGEN_DB_PASSWORD", "password")

// alpine java docker image for smaller size - "azul/zulu-openjdk-alpine:11-jre-headless"
lazy val dockerJavaImage =
  sys.env.getOrElse("DOCKER_JAVA_IMAGE", "openjdk:11-jre-slim-buster")

lazy val flyway = (project in file("modules/flyway"))
  .enablePlugins(FlywayPlugin)
  .settings(
    libraryDependencies += "org.flywaydb" % "flyway-core" % FlywayVersion,
    flywayLocations := Seq("classpath:db/migration/default"),
    flywayUrl := databaseUrl,
    flywayUser := databaseUser,
    flywayPassword := databasePassword,
    flywayBaselineOnMigrate := true
  )

lazy val root = (project in file("."))
  .enablePlugins(CodegenPlugin, DockerPlugin, JavaAppPackaging, AshScriptPlugin)
  .configs(IntegrationTest)
  .settings(
    organization := "wow.doge",
    name := "http4s-demo",
    // version := releaseVersion.getOrElse(dynver.value),
    version in Docker := sys.env
      .getOrElse(
        "DOCKER_PUBLISH_TAG", {
          val s = version.value
          if (s.startsWith("v")) s.tail else s
        }
      ),
    dockerBaseImage := dockerJavaImage,
    dockerExposedPorts := Seq(8081),
    dockerUsername := Some("rohansircar"),
    Defaults.itSettings,
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-deprecation",
      "-feature",
      "-language:existentials",
      "-language:experimental.macros",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-unchecked",
      "-Xlint",
      "-Ywarn-numeric-widen",
      "-Ymacro-annotations",
      //silence warnings for by-name implicits
      "-Wconf:cat=lint-byname-implicit:s",
      //give errors on non exhaustive matches
      "-Wconf:msg=match may not be exhaustive:e",
      "-explaintypes" // Explain type errors in more detail.
    ),
    javacOptions ++= Seq("-source", "11", "-target", "11"),
    //format: off
    libraryDependencies ++= Seq(
      "org.http4s"      %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s"      %% "http4s-blaze-client" % Http4sVersion,
      "org.http4s"      %% "http4s-circe"        % Http4sVersion,
      "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
      "io.circe"        %% "circe-generic"       % CirceVersion,
      "org.scalameta"   %% "munit"               % MunitVersion           % "it,test",
      "org.typelevel"   %% "munit-cats-effect-2" % MunitCatsEffectVersion % "it,test",
      "ch.qos.logback"  %  "logback-classic"     % LogbackVersion,
      "org.scalameta"   %% "svm-subs"            % "20.2.0",
      "co.fs2" %% "fs2-reactive-streams" % "2.5.0",
    ),
    //format: on
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % "3.3.0",
      // "io.monix" %% "monix-bio" % "1.1.0",
      "com.github.monix" % "monix-bio" % "0a2ad29275",
      "io.circe" %% "circe-core" % "0.13.0",
      "io.circe" %% "circe-generic" % "0.13.0",
      "com.softwaremill.sttp.client" %% "core" % "2.2.9",
      "com.softwaremill.sttp.client" %% "monix" % "2.2.9",
      "com.softwaremill.sttp.client" %% "circe" % "2.2.9",
      "com.softwaremill.sttp.client" %% "httpclient-backend-monix" % "2.2.9",
      "com.softwaremill.quicklens" %% "quicklens" % "1.6.1",
      "com.softwaremill.common" %% "tagging" % "2.2.1",
      "com.softwaremill.macwire" %% "macros" % "2.3.6" % "provided",
      "com.github.valskalla" %% "odin-monix" % "0.9.1",
      "com.github.valskalla" %% "odin-slf4j" % "0.9.1",
      "com.github.valskalla" %% "odin-json" % "0.9.1",
      "com.github.valskalla" %% "odin-extras" % "0.9.1",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "com.lihaoyi" %% "os-lib" % "0.7.1",
      "com.beachape" %% "enumeratum" % "1.6.1",
      "com.chuusai" %% "shapeless" % "2.3.3",
      "com.lihaoyi" %% "sourcecode" % "0.2.1",
      "eu.timepit" %% "refined" % "0.9.19",
      "com.zaxxer" % "HikariCP" % "3.4.2",
      "com.typesafe.slick" %% "slick" % "3.3.2",
      "com.typesafe.slick" %% "slick-hikaricp" % "3.3.2",
      "com.h2database" % "h2" % "1.4.199",
      "org.postgresql" % "postgresql" % "42.2.18",
      "com.github.pureconfig" %% "pureconfig" % "0.14.0",
      "io.scalaland" %% "chimney" % "0.6.0",
      "com.rms.miu" %% "slick-cats" % "0.10.4",
      "com.kubukoz" %% "slick-effect" % "0.3.0",
      "io.circe" %% "circe-fs2" % "0.13.0",
      // "org.scalameta" %% "munit" % "0.7.23" % "it,test",
      "de.lolhens" %% "munit-tagless-final" % "0.0.1" % "it,test",
      "org.scalameta" %% "munit-scalacheck" % "0.7.23" % "it,test",
      "org.scalacheck" %% "scalacheck" % "1.15.3" % "it,test",
      "com.dimafeng" %% "testcontainers-scala-munit" % "0.39.3" % IntegrationTest,
      "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.39.3" % IntegrationTest
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    testFrameworks += new TestFramework("munit.Framework")
  )
  .settings(
    slickCodegenDatabaseUrl := databaseUrl,
    slickCodegenDatabaseUser := databaseUser,
    slickCodegenDatabasePassword := databasePassword,
    slickCodegenDriver := _root_.slick.jdbc.PostgresProfile,
    slickCodegenJdbcDriver := "org.postgresql.Driver",
    slickCodegenOutputPackage := "wow.doge.http4sdemo.slickcodegen",
    slickCodegenExcludedTables := Seq("schema_version"),
    slickCodegenCodeGenerator := { (model: m.Model) =>
      new SourceCodeGenerator(model) {
        override def Table = new Table(_) {
          override def Column = new Column(_) {
            override def rawType = model.tpe match {
              case "java.sql.Timestamp" =>
                "java.time.LocalDateTime" // kill j.s.Timestamp
              case _ =>
                super.rawType
            }
          }
        }
      }
    },
    sourceGenerators in Compile += slickCodegen.taskValue
  )
  .dependsOn(flyway)

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.4.3"
inThisBuild(
  List(
    scalaVersion := scalaVersion.value, // 2.11.12, or 2.13.3
    semanticdbEnabled := true, // enable SemanticDB
    semanticdbVersion := "4.4.2" // use Scalafix compatible version
  )
)
