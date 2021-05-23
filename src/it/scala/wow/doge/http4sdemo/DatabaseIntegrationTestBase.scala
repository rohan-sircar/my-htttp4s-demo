package wow.doge.http4sdemo

import com.dimafeng.testcontainers.ContainerDef
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import com.typesafe.config.ConfigFactory
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import monix.execution.Scheduler
import org.testcontainers.utility.DockerImageName
import slick.jdbc.JdbcBackend
import wow.doge.http4sdemo.MonixBioSuite
import wow.doge.http4sdemo.profile.ExtendedPgProfile

trait DatabaseIntegrationTestBase
    extends MonixBioSuite
    with TestContainerForAll {
  def databaseName = "testcontainer-scala"
  def username = "scala"
  def password = "scala"

  override val containerDef: ContainerDef = PostgreSQLContainer.Def(
    dockerImageName = DockerImageName.parse("postgres:12-alpine"),
    databaseName = databaseName,
    username = username,
    password = password
  )

  lazy val profile: ExtendedPgProfile = ExtendedPgProfile

  def config(url: String) = ConfigFactory.parseString(s"""|
                               |testDatabase = {
                               |    url = "$url"
                               |    driver = org.postgresql.Driver
                               |    user = $username
                               |    password = $password
                               |
                               |    numThreads = 2
                               |
                               |    queueSize = 50
                               |
                               |    maxThreads = 2
                               |
                               |    maxConnections = 2
                               |
                               |}""".stripMargin)

  def withDb[T](url: String)(f: JdbcBackend.DatabaseDef => Task[T]) = Task(
    JdbcBackend.Database.forConfig("testDatabase", config(url))
  ).bracket(f)(db => UIO(db.close()))

  def createSchema(containers: Containers) = {
    implicit val s = Scheduler.global
    containers match {
      case container: PostgreSQLContainer =>
        val config = JdbcDatabaseConfig(
          container.jdbcUrl,
          "org.postgresql.Driver",
          Some(username),
          Some(password),
          "flyway_schema_history",
          List("classpath:db/migration/default")
        )
        DBMigrations.migrate[Task](config).runSyncUnsafe(munitTimeout)
      case _ => ()
    }
  }

  def withContainersIO[A](pf: PartialFunction[Containers, Task[A]]): Task[A] = {
    withContainers { containers =>
      pf.applyOrElse(
        containers,
        (c: Containers) =>
          IO.terminate(new Exception(s"Unknown container: ${c.toString}"))
      )
    }
  }

}
