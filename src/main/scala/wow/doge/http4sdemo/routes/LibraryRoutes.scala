package wow.doge.http4sdemo.routes

import cats.syntax.show._
import io.circe.Json
import io.odin.Logger
import io.odin.syntax._
import monix.bio.IO
import monix.bio.Task
import monix.reactive.Consumer
import monix.reactive.Observable
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import wow.doge.http4sdemo.AppError
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.BookSearchMode
import wow.doge.http4sdemo.models.BookUpdate
import wow.doge.http4sdemo.models.NewBook
import wow.doge.http4sdemo.models.NewExtra
import wow.doge.http4sdemo.models.Refinements._
import wow.doge.http4sdemo.models.pagination._
import wow.doge.http4sdemo.services.LibraryService
import wow.doge.http4sdemo.utils.extractReqId

class LibraryRoutes(libraryService: LibraryService, logger: Logger[Task]) {

  val routes: HttpRoutes[Task] = {
    val dsl = Http4sDsl[Task]
    import dsl._
    object BookSearchQuery extends QueryParamDecoderMatcher[SearchQuery]("q")
    object StringSearchQuery extends QueryParamDecoderMatcher[String]("q")
    HttpRoutes.of[Task] {

      case req @ GET -> Root / "api" / "books" / "search" :?
          BookSearchMode.Matcher(mode) +& BookSearchQuery(query) =>
        import wow.doge.http4sdemo.utils.observableArrayJsonEncoder
        import io.circe.syntax._
        IO.deferAction(implicit s =>
          for {
            reqId <- IO.pure(extractReqId(req))
            clogger = logger.withConstContext(
              Map(
                "name" -> "Search book",
                "request-id" -> reqId,
                "request-uri" -> req.uri.toString,
                "mode" -> mode.entryName,
                "query" -> query.value
              )
            )
            _ <- clogger.debugU("Request to search book")
            books = libraryService.searchBooks(mode, query)
            res <- Ok(books.map(_.asJson))
          } yield res
        )

      case req @ GET -> Root / "api" / "books" :?
          PaginationLimit.matcher(limit) +& PaginationPage.matcher(page) =>
        import wow.doge.http4sdemo.utils.observableArrayJsonEncoder
        import io.circe.syntax._
        IO.deferAction(implicit s =>
          for {
            reqId <- IO.pure(extractReqId(req))
            pagination = Pagination(page, limit)
            clogger = logger.withConstContext(
              Map(
                "name" -> "Get books",
                "request-id" -> reqId,
                "request-uri" -> req.uri.toString,
                "pagination" -> pagination.toString
              )
            )
            _ <- clogger.infoU("Request for books")
            books = libraryService.getBooks(pagination)
            res <- Ok(books.map(_.asJson))
          } yield res
        )

      // case GET -> Root / "api" / "books" =>
      //   import wow.doge.http4sdemo.utils.observableArrayJsonEncoder
      //   import io.circe.syntax._
      //   IO.deferAction(implicit s =>
      //     for {
      //       _ <- logger.infoU("Got request for books")
      //       books = libraryService.getBooks
      //       res <- Ok(books.map(_.asJson))
      //     } yield res
      //   )

      case req @ GET -> Root / "api" / "books" / BookId(id) =>
        import org.http4s.circe.CirceEntityCodec._
        for {
          reqId <- IO.pure(extractReqId(req))
          clogger = logger.withConstContext(
            Map(
              "name" -> "Get book",
              "request-id" -> reqId,
              "request-uri" -> req.uri.toString,
              "book-id" -> id.toString
            )
          )
          _ <- clogger.infoU(s"Retrieving book")
          bookJson <- libraryService.getBookById(id)
          res <- bookJson.fold(
            clogger.warnU(s"Request for non-existent book") >>
              AppError
                .EntityDoesNotExist(s"Book with id $id does not exist")
                .toResponse
          )(b => Ok(b).hideErrors)
        } yield res

      case req @ PUT -> Root / "api" / "books" =>
        import org.http4s.circe.CirceEntityCodec._
        for {
          reqId <- IO.pure(extractReqId(req))
          newBook <- req.as[NewBook]
          clogger = logger.withConstContext(
            Map(
              "name" -> "Create book",
              "request-id" -> reqId,
              "new-book-data" -> newBook.toString
            )
          )
          res <- libraryService
            .createBook(newBook)
            .tapError(err => clogger.errorU(err.toString))
            .flatMap(book => Created(book).hideErrors)
            .onErrorHandleWith(_.toResponse)
        } yield res

      case req @ PATCH -> Root / "api" / "books" / BookId(id) =>
        import org.http4s.circe.CirceEntityCodec._
        for {
          reqId <- IO.pure(extractReqId(req))
          updateData <- req.as[BookUpdate]
          clogger = logger.withConstContext(
            Map(
              "name" -> "Update book",
              "request-id" -> reqId,
              "book-id" -> id.toString,
              "update-data" -> updateData.toString
            )
          )
          res <- libraryService
            .updateBook(id, updateData)
            .flatMap(_ => NoContent().hideErrors)
            .tapError(err => clogger.errorU(err.toString))
            .onErrorHandleWith(_.toResponse)
        } yield res

      case req @ DELETE -> Root / "api" / "books" / BookId(id) =>
        for {
          reqId <- IO.pure(extractReqId(req))
          clogger = logger.withConstContext(
            Map(
              "name" -> "Delete book",
              "request-id" -> reqId,
              "book-id" -> id.toString
            )
          )
          _ <- clogger.debug("Request to delete book")
          _ <- libraryService.deleteBook(id)
          res <- Ok()
        } yield res

      //TODO: use convenience method for decoding json stream
      case req @ POST -> Root / "api" / "books" =>
        import org.http4s.circe.CirceEntityCodec._
        import wow.doge.http4sdemo.utils.observableArrayJsonDecoder
        IO.deferAction(implicit s =>
          for {
            // newBooks <- req.as[List[Book]]
            newBooks <- req.as[Observable[Json]]
            numRows <- newBooks
              .mapEvalF(_.as[NewBook])
              .bufferTumbling(50)
              .scanEvalF(Task.pure(NumRows(0))) { case (numRows, books) =>
                libraryService
                  .createBooks(books.toList)
                  .map(o => numRows :+ o.getOrElse(NumRows(0)))
              }
              .consumeWith(Consumer.foldLeft(NumRows(0))(_ :+ _))
              .toIO
            res <- Ok(numRows)
          } yield res
        )

      case req @ GET -> Root / "api" / "extras" / "search" :?
          StringSearchQuery(q) =>
        import wow.doge.http4sdemo.utils.observableArrayJsonEncoder
        import io.circe.syntax._
        IO.deferAction(implicit s =>
          for {
            reqId <- IO.pure(extractReqId(req))
            clogger = logger.withConstContext(
              Map(
                "name" -> "Get books",
                "request-id" -> reqId,
                "request-uri" -> req.uri.toString,
                "q" -> q
              )
            )
            _ <- clogger.infoU("Request for searching extras")
            extras = libraryService.searchExtras(q)
            res <- Ok(extras.map(_.asJson))
          } yield res
        )

      case req @ PUT -> Root / "api" / "extras" =>
        import org.http4s.circe.CirceEntityCodec._
        for {
          reqId <- IO.pure(extractReqId(req))
          newExtra <- req.as[NewExtra]
          clogger = logger.withConstContext(
            Map(
              "name" -> "Create extra",
              "request-id" -> reqId,
              "new-extra-data" -> newExtra.show
            )
          )
          _ <- clogger.infoU("Request for creating extras")
          res <- libraryService
            .createExtra(newExtra)
            .flatMap(id => Created(id).hideErrors)
          // .onErrorHandleWith(_.toResponse)
        } yield res
    }
  }

}
