package protocols

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import protocols.DateMarshalling._

trait TaskJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  import actors.TaskManager._

  implicit val objectTypeFormat: RootJsonFormat[TaskEntity] = jsonFormat10(TaskEntity)
  implicit val addObjTypeFormat: RootJsonFormat[TaskModel] = jsonFormat4(TaskModel)
}
