package protocols

import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import protocols.DateMarshalling._


trait TaskTypeJsonProtocol extends DefaultJsonProtocol {
  import actors.TaskType._

  implicit val taskTypeFormat: RootJsonFormat[TaskTypeEntity] = jsonFormat5(TaskTypeEntity)
  implicit val addTaskTypeFormat: RootJsonFormat[TaskTypeModel] = jsonFormat2(TaskTypeModel)
}
