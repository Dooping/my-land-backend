package protocols

import spray.json.{DefaultJsonProtocol, RootJsonFormat}


trait TaskTypeJsonProtocol extends DefaultJsonProtocol {
  import actors.TaskType._

  implicit val taskTypeFormat: RootJsonFormat[TaskTypeEntity] = jsonFormat3(TaskTypeEntity)
  implicit val addTaskTypeFormat: RootJsonFormat[TaskTypeModel] = jsonFormat2(TaskTypeModel)
}
