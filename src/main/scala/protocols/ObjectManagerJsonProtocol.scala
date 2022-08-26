package protocols

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import protocols.DateMarshalling._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}


trait ObjectManagerJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  import actors.ObjectManager._

  implicit val landObjectFormat: RootJsonFormat[LandObject] = jsonFormat6(LandObject)
  implicit val changeObjectFormat: RootJsonFormat[ChangeLandObject] = jsonFormat4(ChangeLandObject)
  implicit val addObjectFormat: RootJsonFormat[AddLandObject] = jsonFormat3(AddLandObject)
}
