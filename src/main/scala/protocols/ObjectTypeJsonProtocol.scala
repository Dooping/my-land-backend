package protocols

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import protocols.DateMarshalling._


trait ObjectTypeJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  import actors.ObjectType._

  implicit val objectTypeFormat: RootJsonFormat[ObjectTypeEntity] = jsonFormat6(ObjectTypeEntity)
  implicit val addObjTypeFormat: RootJsonFormat[ObjType] = jsonFormat3(ObjType)
  implicit val addObjectTypeFormat: RootJsonFormat[AddObjectType] = jsonFormat1(AddObjectType)
}
