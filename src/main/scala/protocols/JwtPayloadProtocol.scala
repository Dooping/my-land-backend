package protocols

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait JwtPayloadProtocol  extends SprayJsonSupport with DefaultJsonProtocol{
  import utils.JwtHelper.Payload

  implicit val payloadFormat: RootJsonFormat[Payload] = jsonFormat1(Payload)

}
