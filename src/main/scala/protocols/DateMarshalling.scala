package protocols

import java.text._
import java.util._
import scala.util.Try
import spray.json._

object DateMarshalling {
  implicit object DateFormat extends JsonFormat[Date] {
    def write(date: Date): JsString = JsString(dateToIsoString(date))
    def read(json: JsValue): Date = json match {
      case JsString(rawDate) =>
        parseIsoDateString(rawDate)
          .fold(deserializationError(s"Expected ISO Date format, got $rawDate"))(identity)
      case error => deserializationError(s"Expected JsString, got $error")
    }
  }

  private val localIsoDateFormatter = new ThreadLocal[SimpleDateFormat] {
    override def initialValue() = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  }

  private def dateToIsoString(date: Date) =
    localIsoDateFormatter.get().format(date)

  private def parseIsoDateString(date: String): Option[Date] =
    Try{ localIsoDateFormatter.get().parse(date) }.toOption
}
