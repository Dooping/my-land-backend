package protocols

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}


trait TemplateJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with ObjectTypeJsonProtocol {
  import actors.Template._

  implicit val responseTypeFormat: RootJsonFormat[ObjectTypeOptionsResponse] = jsonFormat2(ObjectTypeOptionsResponse)
  implicit val registerTypeFormat: RootJsonFormat[RegisterNewLandTemplate] = jsonFormat3(RegisterNewLandTemplate)
  implicit val changeTypeFormat: RootJsonFormat[ChangeLandTemplate] = jsonFormat3(ChangeLandTemplate)
}
