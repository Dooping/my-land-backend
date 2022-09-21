package protocols

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}


trait TaskTypeJsonProtocol extends DefaultJsonProtocol {
  import actors.TaskType._

  implicit val taskTypeFormat: RootJsonFormat[TaskTypeEntity] = jsonFormat3(TaskTypeEntity)
  implicit val addTaskTypeFormat: RootJsonFormat[TaskTypeModel] = jsonFormat2(TaskTypeModel)
}
