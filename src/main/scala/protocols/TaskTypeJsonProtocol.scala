package protocols

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import protocols.DateMarshalling._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}


trait TaskTypeJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  import actors.TaskType._

  implicit val objectTypeFormat: RootJsonFormat[TaskTypeEntity] = jsonFormat3(TaskTypeEntity)
  implicit val addObjTypeFormat: RootJsonFormat[TaskTypeModel] = jsonFormat2(TaskTypeModel)
}
