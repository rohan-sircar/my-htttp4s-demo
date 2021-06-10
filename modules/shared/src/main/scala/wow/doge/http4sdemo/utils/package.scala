package wow.doge.http4sdemo

import io.circe.Decoder
import io.odin.Logger
import io.odin.meta.Position
import monix.bio.IO
import monix.bio.Task
import wow.doge.http4sdemo.implicits._

package object utils {
  def observableFromByteStreamA[A: Decoder](stream: fs2.Stream[Task, Byte]) =
    stream.chunks
      .through(io.circe.fs2.byteArrayParserC)
      .through(io.circe.fs2.decoder)
      .toObs

  def infoSpan[E, A](
      fa: IO[E, A]
  )(implicit logger: Logger[Task], position: Position) =
    logger
      .infoU("Span - Begin")
      .bracket(_ => fa)(_ => logger.infoU("Span - End"))

}
