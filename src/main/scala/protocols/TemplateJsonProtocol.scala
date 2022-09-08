package protocols

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}


trait TemplateJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with ObjectTypeJsonProtocol {
  import actors.Template._

  implicit val responseTypeFormat: RootJsonFormat[ObjectTypeOptionsResponse] = jsonFormat2(ObjectTypeOptionsResponse)
  implicit val registerTypeFormat: RootJsonFormat[RegisterNewLandObjectTemplate] = jsonFormat3(RegisterNewLandObjectTemplate)
  implicit val changeTypeFormat: RootJsonFormat[ChangeLandObjectTemplate] = jsonFormat3(ChangeLandObjectTemplate)
}
