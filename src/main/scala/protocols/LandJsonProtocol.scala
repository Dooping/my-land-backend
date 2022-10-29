package protocols

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import protocols.DateMarshalling._


trait LandJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  import actors.Land._

  implicit val landFormat: RootJsonFormat[LandEntity] = jsonFormat11(LandEntity)
  implicit val addLandFormat: RootJsonFormat[AddLand] = jsonFormat8(AddLand)
  implicit val changeLandDescriptionFormat: RootJsonFormat[ChangeLandDescription] = jsonFormat2(ChangeLandDescription)
  implicit val changeLandPolygonFormat: RootJsonFormat[ChangePolygon] = jsonFormat7(ChangePolygon)
}
